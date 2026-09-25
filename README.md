# 🎬 Video Hosting

Аналог YouTube с загрузкой, обработкой и адаптивным стримингом видео. Построен на **Spring Boot 3** с использованием **микросервисной архитектуры**, **Apache Kafka**, **MinIO (S3)** и **HLS** для потоковой передачи.

## 🏗️ Архитектура

```
                          ┌─────────────────┐
                          │    Frontend     │
                          │   (Nginx + JS)  │
                          └────────┬────────┘
                                   │ HTTP
                                   ▼
                          ┌─────────────────┐
                          │   API Gateway   │  ← JWT validation, routing
                          │ (Spring Cloud)  │
                          └────────┬────────┘
                                   │
        ┌──────────────┬───────────┼────────────┬──────────────┬
        │              │           │            │              │
        ▼              ▼           ▼            ▼              ▼
 ┌────────────┐ ┌────────────┐ ┌─────────┐ ┌─────────────┐ ┌────────────┐
 │Auth Service│ │Upload Svc  │ │Stream   │ │Processor    │ │  Kafka     │
 │            │ │            │ │Service  │ │Service      │ │ (KRaft)    │
 └─────┬──────┘ └─────┬──────┘ └────┬────┘ └──────┬──────┘ └────────────┘
       │              │             │              │
       │              │  publish    │              │ consume
       │              └─────────────┼──────────────┘
       │                            │
       ▼                            ▼
 ┌──────────────┐          ┌─────────────────┐
 │  PostgreSQL  │          │      MinIO      │
 │              │          │  (S3 storage)   │
 └──────────────┘          └─────────────────┘
```

### Компоненты

| Сервис | Порт | Назначение |
|--------|------|------------|
| **api-gateway** | 8080 | Единая точка входа, валидация JWT, маршрутизация |
| **auth-service** | 8081 | Регистрация, логин, выдача JWT |
| **upload-service** | 8082 | Загрузка видео в MinIO, публикация событий в Kafka |
| **stream-service** | 8083 | Список видео, выдача ссылок на HLS-плейлисты, счётчик просмотров |
| **processor-service** | 8084 | Асинхронная обработка видео (FFmpeg → HLS) |
| **frontend** | 8085 | Статические страницы (login, dashboard, player) |
| **PostgreSQL** | 5432 | Хранение пользователей и метаданных видео |
| **MinIO** | 9000 / 9001 | Объектное хранилище (S3-совместимое) |
| **Kafka** | 9092 | Брокер сообщений между сервисами |

## 🛠️ Технологический стек

### Backend
- **Java 17**, **Spring Boot 3.3**
- **Spring Cloud Gateway** — API Gateway
- **Spring Security + JWT** — аутентификация и авторизация
- **Spring Data JPA / Hibernate** — работа с БД
- **Spring Kafka** — event-driven взаимодействие
- **Apache Kafka (KRaft)** — брокер сообщений
- **MinIO Java SDK** — работа с S3-совместимым хранилищем
- **FFmpeg** — конвертация видео в HLS
- **Lombok** — сокращение шаблонного кода

### Frontend
- HTML5, Vanilla JS
- **hls.js** — воспроизведение HLS в браузере
- **Bootstrap 5** — UI

### Инфраструктура
- **Docker / Docker Compose** — контейнеризация и оркестрация
- **PostgreSQL 16** — основная БД
- **MinIO** — объектное хранилище
- **Nginx** — раздача статики

## ✨ Возможности

- 🔐 **Аутентификация** через JWT (регистрация, логин)
- 📤 **Загрузка видео** с валидацией типа и размера
- ⚙️ **Асинхронная обработка** через Kafka и отдельный воркер
- 🎞️ **Адаптивный стриминг** (HLS, мастер-плейлист с несколькими качествами)
- 👁️ **Счётчик просмотров** с инкрементом через JPA
- 🗑️ **Удаление видео** вместе со всеми HLS-сегментами из MinIO
- 🌐 **Микросервисная архитектура** с общей точкой входа
- 🐳 **Полная контейнеризация** 

## 🚀 Быстрый старт

### Требования
- Docker Desktop (Windows / macOS / Linux)
- JDK 17+ (для локальной сборки)
- Maven 3.8+

### Шаги

1. **Клонируй репозиторий**
   ```bash
   git clone https://github.com/your-username/video-service.git
   cd video-service
   ```

2. **Собери все JAR-файлы**
   ```bash
   mvn clean package -DskipTests
   ```

3. **Запусти инфраструктуру и микросервисы**
   ```bash
   docker-compose up -d
   ```

4. **Открой в браузере**
   - Приложение: [http://localhost:8080/login.html](http://localhost:8080/login.html)
   - MinIO Console: [http://localhost:9001](http://localhost:9001) (`minioadmin / minioadmin`)

5. **Зарегистрируйся, загрузи видео и нажми «Смотреть»**

## 📡 API Endpoints (через Gateway)

### Аутентификация
| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/api/auth/register` | Регистрация |
| POST | `/api/auth/login` | Логин, возврат JWT |

### Видео
| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/videos` | Список видео пользователя |
| POST | `/api/videos/upload` | Загрузка видео |
| DELETE | `/api/videos/{id}` | Удаление видео |
| GET | `/api/stream/{id}/playlist-url` | Получить ссылку на HLS-плейлист |

### Внутренние (между сервисами)
| Метод | Путь | Описание |
|-------|------|----------|
| PATCH | `/internal/videos/{id}/status` | Обновить статус видео (для processor-service) |

**Авторизация:** заголовок `Authorization: Bearer <JWT>`. Gateway проверяет токен и передаёт downstream `X-User-Id`.

## 📁 Структура проекта

```
video-service/
├── api-gateway/           # Spring Cloud Gateway + JWT-фильтр
├── auth-service/          # Регистрация, логин, выдача JWT
├── upload-service/        # Загрузка в MinIO + Kafka producer
├── stream-service/        # Список видео, HLS-ссылки
├── processor-service/     # Kafka consumer + FFmpeg → HLS
├── frontend/              # HTML/CSS/JS + Nginx
├── docker-compose.yml     # Оркестрация всех сервисов
└── pom.xml                # Родительский POM
```

## 🔄 Как работает обработка видео

1. **Upload Service** принимает файл, кладёт в MinIO (`videos/{userId}/{uuid}.mp4`), сохраняет запись в БД со статусом `UPLOADED`.
2. Публикует `VideoUploadedEvent(videoId, filePath, userId)` в Kafka-топик `video.uploaded`.
3. **Processor Service** слушает топик, скачивает файл, запускает FFmpeg:
   ```
   ffmpeg -i input.mp4 -s 1280x720 -hls_time 10 -f hls playlist_720.m3u8
   ffmpeg -i input.mp4 -s 640x360  -hls_time 10 -f hls playlist_360.m3u8
   ```
4. Создаётся `master.m3u8` со ссылками на оба качества.
5. Сегменты загружаются в MinIO (`videos/{userId}/{videoId}/hls/`).
6. Processor вызывает `PATCH /internal/videos/{id}/status` → статус `READY`.
7. **Stream Service** отдаёт клиенту публичный URL на `master.m3u8`, а hls.js сам подгружает сегменты нужного качества.

## 🧪 Известные ограничения

- Видео загружается **целиком** — для больших файлов нужна **чанковая загрузка** (chunked upload).
- Нет **rate limiting** на загрузку — можно залить много видео подряд.
- Нет **кеширования** (Redis не задействован).
- **HLS** создаётся для двух качеств; можно расширить (1080p, 480p).
- Обработка видео **CPU-bound** — FFmpeg сильно нагружает процессор.

## 🗺️ Roadmap

- [ ] Чанковая загрузка видео (chunked upload)
- [ ] Rate limiting через Redis
- [ ] Кеширование списков видео
- [ ] Категории и теги
- [ ] Полнотекстовый поиск
- [ ] Миниатюры (poster)
- [ ] Тесты (JUnit + Testcontainers)
- [ ] Prometheus + Grafana для мониторинга
- [ ] Kubernetes-манифесты

## 👤 Автор

**Дмитрий** — Java-разработчик  
Проект создан как демонстрация навыков в микросервисной архитектуре, event-driven разработке и работе с видео.
