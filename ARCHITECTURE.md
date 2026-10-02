# Архитектура Video Hosting

Документ описывает высокоуровневую архитектуру, компоненты, потоки данных и ключевые технические решения.

## Содержание
* [Обзор системы](#обзор-системы)
* [Компоненты системы](#компоненты-системы)
    * [Приложения](#приложения)
    * [Инфраструктура](#инфраструктура)
* [Хранение данных и Kafka](#хранение-данных-и-kafka)
    * [Data Ownership](#data-ownership)
    * [Kafka-топики и Avro](#kafka-топики-и-avro)
* [Ключевые потоки данных](#ключевые-потоки-данных)
    * [Загрузка (Chunked Multipart)](#загрузка-chunked-multipart)
    * [Обработка (Kafka → FFmpeg → HLS)](#обработка-kafka--ffmpeg--hls)
    * [Стриминг](#стриминг)
    * [Удаление](#удаление)
* [Ключевые архитектурные решения (Trade-offs)](#ключевые-архитектурные-решения-trade-offs)
* [Известные ограничения](#известные-ограничения)


## 1. Обзор системы
Микросервисная платформа для загрузки, обработки и стриминга видео (аналог YouTube).
*   **Стек:** Java, PostgreSQL, Kafka (KRaft), Avro, MinIO (S3), Redis, Nginx, FFmpeg.
*   **Ключевые фичи:** JWT-аутентификация, chunked multipart upload, асинхронное транскодирование в HLS (4 качества), live-уведомления через SSE, адаптивный стриминг.
*   **Мониторинг:** Prometheus + Grafana.

## 2. Компоненты системы

### Приложения
*   **API Gateway:** Единая точка входа. Валидация JWT (HS384), извлечение `userId` в заголовок `X-User-Id`, маршрутизация, агрегация Swagger, поддержка SSE (через query-параметр). Полностью stateless.
*   **Auth Service:** Регистрация, логин, выдача JWT. Хеширование BCrypt. Владелец `auth_db`.
*   **Upload Service:** Инициализация и завершение multipart upload в S3. Владелец таблицы `videos`. Публикует события в Kafka. Обновляет статусы видео.
*   **Stream Service:** Каталог видео, генерация публичных HLS-ссылок. Счётчик просмотров через Redis INCR (batch flush раз в 30 сек). Кеш каталога в Redis. Только чтение `video_db`. Слушает Kafka для инвалидации кеша.
*   **Notification Service:** SSE-эндпоинт. Слушает Kafka (fan-out). Хранит активные соединения в памяти.
*   **Processor Service:** CPU-bound. Слушает Kafka, персистит задачи в БД, обрабатывает видео через FFmpeg в HLS, загружает сегменты в MinIO, публикует `video.processed`. Владелец `processor_db`. Восстановление задач через heartbeat. Два режима: `fast` (360p) и `slow` (240p, 480p, 720p).
*   **Frontend:** Nginx + статика.

### Инфраструктура
*   **PostgreSQL:** 3 инстанса: `auth_db`, `video_db`, `processor_db`.
*   **MinIO:** S3-совместимое хранилище оригиналов и HLS-сегментов.
*   **MinIO Cache (Nginx):** Reverse-proxy перед MinIO. Кеширует `.ts` сегменты на 24 часа, не кеширует `.m3u8`.
*   **Redis:** Кеш каталога, счётчики просмотров, сессии upload.
*   **Kafka + Schema Registry:** Брокер сообщений и управление Avro-схемами.

## 3. Хранение данных и Kafka

### Data Ownership
| Ресурс | Владелец (Writer) | Читатели                   |
|---|---|----------------------------|
| `users` (`auth_db`) | auth-service | никто (только через API)   |
| `videos` (`video_db`) | upload-service | stream-service (read-only) |
| `video_processing_tasks` (`processor_db`) | processor-service | никто                      |
| MinIO: оригиналы | upload-service | processor-service          |
| MinIO: HLS | processor-service | пользователь               |
| Redis | stream-service, upload-service | никто                      |

### Kafka-топики и Avro
| Топик | Продюсер | Консьюмеры |
|---|---|---|
| `video.uploaded` | upload-service | processor-fast, processor-slow |
| `video.processed` | processor-* | upload-service, notification-service, stream-service |
| `video.deleted` | upload-service | processor-*, stream-service |

*   **Схемы:** Avro + Schema Registry. Обратная совместимость (новые поля с `default`). `ErrorHandlingDeserializer` для пропуска битых сообщений.
*   **Offset Reset:** `latest` для processor и upload (recovery из БД догонит), `earliest` для stream (идемпотентный evict кеша).
*   **Fan-out:** Fast и slow используют разные consumer groups. Notification Service использует уникальный `group.id` на инстанс для broadcast.

## 4. Ключевые потоки данных

### 4.1. Загрузка (Chunked Multipart)
1.  Клиент запрашивает `/init` у upload-service.
2.  Сервис создаёт multipart upload в MinIO, генерирует presigned URL для частей, сохраняет сессию в Redis (TTL 24ч).
3.  Браузер грузит части **напрямую в MinIO** параллельно (3 в полёте).
4.  Клиент вызывает `/complete`. Сервис завершает upload, создаёт запись в `videos` (статус `UPLOADED`), публикует `VideoUploadedEvent`, удаляет сессию из Redis.

### 4.2. Обработка (Kafka → FFmpeg → HLS)
1.  Fast и slow консьюмеры читают `VideoUploadedEvent`.
2.  Персистят задачи в `processor_db` (`ON CONFLICT DO NOTHING`). Offset коммитится сразу.
3.  Пул потоков забирает задачи через `SELECT ... FOR UPDATE SKIP LOCKED`.
4.  FFmpeg запускается с `filter_complex` (один проход на все качества).
5.  HeartbeatService обновляет `heartbeat_at` каждые 30 сек.
6.  После FFmpeg: проверка `isCancelled` (если пришло `video.deleted`), загрузка HLS в MinIO, `markDone`, сборка `master.m3u8` из **всех** DONE-качеств, публикация `VideoProcessedEvent`.

### 4.3. Стриминг
1.  Клиент запрашивает `/playlist-url` у stream-service.
2.  Сервис генерирует публичную ссылку на `master.m3u8`, инкрементит счётчик просмотров в Redis.
3.  hls.js грузит `.m3u8` и `.ts` через minio-cache. `.m3u8` — всегда MISS, `.ts` — HIT (99%).

### 4.4. Удаление
1.  Клиент вызывает `DELETE /api/videos/{id}`.
2.  upload-service удаляет HLS и оригинал из MinIO, запись из БД, публикует `VideoDeletedEvent`.
3.  Процессоры помечают задачи `CANCELLED` (FFmpeg доработает, но результат не зальётся).
4.  stream-service инвалидирует кеш.

## 5. Ключевые архитектурные решения (Trade-offs)

1.  **Прямая загрузка в S3 (Presigned URLs):** Backend не касается тела файла (экономия heap/CPU). Требует CORS и правильного `Host` header в Nginx.
2.  **Разделение processing на fast/slow:** Убирает конкуренцию за CPU. Позволяет независимо масштабировать и использовать разные машины. Вместо сложного priority scheduler — два простых инстанса.
3.  **Ingestion отдельно от processing:** Kafka-listener не блокируется на FFmpeg. Offset коммитится мгновенно. Обработка — через БД-очередь (`SKIP LOCKED`). Recovery из БД — единственный источник правды.
4.  **Heartbeat-based recovery:** Защита от двойной обработки при падении воркера. Recovery ищет задачи с протухшим heartbeat (>10 мин). (В текущей версии вызывается только при старте).
5.  **Redis sliding-TTL кеш каталога:** Снижает нагрузку на Postgres в 30 раз. Популярные данные не вылетают, заброшенные умирают через 30 сек. Требует явной инвалидации.
6.  **Защита от cache stampede:** Redisson RLock. Первый поток грузит, остальные ждут. Fallback в degraded mode (чтение БД напрямую), если лок не взят за 2 сек.
7.  **Изоляция БД по доменам:** Падение auth-БД не влияет на видео. Нет транзакций между доменами — только eventual consistency через Kafka.
8.  **Отмена обработки через `video.deleted`:** Защита от мусора в MinIO. Три проверки `isCancelled`: до download, после FFmpeg, в catch.
9.  **Сборка `master.m3u8` из всех DONE:** Fast и slow не затирают плейлисты друг друга. Работает при любом порядке завершения.
10. **Nginx-кеш перед MinIO:** Защищает MinIO от 1000+ одновременных запросов. 99% HIT. Nginx становится точкой отказа.

## 6. Известные ограничения
*   **Один инстанс на роль:** В production-режиме не запускалось, но поддерживается `--scale`.
*   **Recovery только при старте:** Для runtime-потерь нужно `@Scheduled`-решение.
*   **CORS через ENV MinIO:** `MINIO_API_CORS_ALLOW_ORIGIN=*` только для dev.
*   **Нет distributed tracing:** OpenTelemetry в roadmap.
*   **1080p не генерируется:** Стоит сделать опциональным.
*   **Cache stampede защита только в stream-service.**
*   **Recovery не чист:** При удалении задачи в `IN_PROGRESS` heartbeat может остаться свежим.
*   **Нет тестов:** JUnit + Testcontainers в roadmap.