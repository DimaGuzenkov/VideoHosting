# 🎬 Video Hosting

Аналог YouTube с загрузкой, обработкой и адаптивным стримингом видео. Построен на **Spring Boot 3** с использованием **микросервисной архитектуры**, **Apache Kafka**, **MinIO (S3)** и **HLS** для потоковой передачи.

## 🏗️ Архитектура
### 👉 **[ARCHITECTURE.md](ARCHITECTURE.md)**

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
* с сентября 2026 года могут быть проблемы со скачиванием образа MinIO. В случае ошибки обратитесь на почту gextnkov.d.2010@gmail.com, вышлю архивированный образ.
4. **Открой в браузере**
   - Приложение: [http://localhost:8080/login.html](http://localhost:8080/login.html)
   - MinIO Console: [http://localhost:9001](http://localhost:9001) (`minioadmin / minioadmin`)

5. **Зарегистрируйся, загрузи видео и нажми «Смотреть»**

## 👤 Автор

**Дмитрий** — Java-разработчик  
Проект создан как демонстрация навыков в микросервисной архитектуре, event-driven разработке и работе с видео.
