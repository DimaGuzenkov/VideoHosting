# ==============================================================================
# Video Service — Makefile
# ==============================================================================
# Все команды для сборки, запуска и управления микросервисами.
# Требования: make, mvn, docker, docker-compose
# ==============================================================================

# --- Переменные -----------------------------------------------------------------
SHELL           := /bin/bash
MAVEN           := mvn
DOCKER_COMPOSE  := docker-compose

# Модули (в порядке сборки)
MODULES := auth-service upload-service stream-service processor-service api-gateway

# Отдельные часто используемые модули
MODULE_AUTH      := auth-service
MODULE_UPLOAD    := upload-service
MODULE_STREAM    := stream-service
MODULE_PROCESSOR := processor-service
MODULE_GATEWAY   := api-gateway

# Цвета для вывода
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
RESET  := \033[0m

# --- Help (по умолчанию) --------------------------------------------------------
.PHONY: help
help: ## Показать список доступных команд
	@echo ""
	@echo "$(GREEN)Video Service — доступные команды:$(RESET)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-25s$(RESET) %s\n", $$1, $$2}'
	@echo ""

# ==============================================================================
# СБОРКА
# ==============================================================================

.PHONY: build
build: ## Собрать все модули (JAR без тестов)
	@echo "$(GREEN)>>> Сборка всех модулей...$(RESET)"
	$(MAVEN) clean package -DskipTests

.PHONY: build-tests
build-tests: ## Собрать все модули с тестами
	@echo "$(GREEN)>>> Сборка всех модулей с тестами...$(RESET)"
	$(MAVEN) clean package

.PHONY: build-module
build-module: ## Собрать один модуль: make build-module MODULE=upload-service
	@if [ -z "$(MODULE)" ]; then \
		echo "$(RED)Укажи модуль: make build-module MODULE=upload-service$(RESET)"; \
		exit 1; \
	fi
	@echo "$(GREEN)>>> Сборка модуля $(MODULE)...$(RESET)"
	$(MAVEN) clean package -pl $(MODULE) -am -DskipTests

.PHONY: build-gateway
build-gateway: ## Собрать api-gateway
	$(MAVEN) clean package -pl $(MODULE_GATEWAY) -am -DskipTests

.PHONY: build-auth
build-auth: ## Собрать auth-service
	$(MAVEN) clean package -pl $(MODULE_AUTH) -am -DskipTests

.PHONY: build-upload
build-upload: ## Собрать upload-service
	$(MAVEN) clean package -pl $(MODULE_UPLOAD) -am -DskipTests

.PHONY: build-stream
build-stream: ## Собрать stream-service
	$(MAVEN) clean package -pl $(MODULE_STREAM) -am -DskipTests

.PHONY: build-processor
build-processor: ## Собрать processor-service
	$(MAVEN) clean package -pl $(MODULE_PROCESSOR) -am -DskipTests

.PHONY: install
install: ## Установить артефакты в локальный репозиторий Maven
	$(MAVEN) clean install -DskipTests

.PHONY: clean
clean: ## Очистить target всех модулей
	@echo "$(GREEN)>>> Очистка target...$(RESET)"
	$(MAVEN) clean

# ==============================================================================
# ЗАПУСК / ОСТАНОВКА (Docker Compose)
# ==============================================================================

.PHONY: up
up: ## Запустить все контейнеры в фоне
	@echo "$(GREEN)>>> Запуск контейнеров...$(RESET)"
	$(DOCKER_COMPOSE) up -d

.PHONY: up-build
up-build: ## Собрать JAR и запустить контейнеры
	$(MAKE) build
	$(DOCKER_COMPOSE) up -d

.PHONY: down
down: ## Остановить и удалить контейнеры (данные сохраняются)
	@echo "$(GREEN)>>> Остановка контейнеров...$(RESET)"
	$(DOCKER_COMPOSE) down

.PHONY: down-clean
down-clean: ## Остановить контейнеры и удалить volumes (данные будут потеряны!)
	@echo "$(RED)>>> ВНИМАНИЕ: удаление всех данных...$(RESET)"
	$(DOCKER_COMPOSE) down -v

.PHONY: restart
restart: ## Перезапустить все контейнеры
	$(DOCKER_COMPOSE) restart

.PHONY: restart-module
restart-module: ## Перезапустить один сервис: make restart-module MODULE=auth-service
	@if [ -z "$(MODULE)" ]; then \
		echo "$(RED)Укажи модуль: make restart-module MODULE=auth-service$(RESET)"; \
		exit 1; \
	fi
	$(DOCKER_COMPOSE) restart $(MODULE)

.PHONY: recreate-module
recreate-module: ## Пересобрать и пересоздать один сервис: make recreate-module MODULE=upload-service
	@if [ -z "$(MODULE)" ]; then \
		echo "$(RED)Укажи модуль: make recreate-module MODULE=upload-service$(RESET)"; \
		exit 1; \
	fi
	$(DOCKER_COMPOSE) up -d --force-recreate $(MODULE)

# ==============================================================================
# ЛОГИ
# ==============================================================================

.PHONY: logs
logs: ## Показать логи всех контейнеров (Ctrl+C для выхода)
	$(DOCKER_COMPOSE) logs -f

.PHONY: logs-gateway
logs-gateway: ## Логи api-gateway
	$(DOCKER_COMPOSE) logs -f api-gateway

.PHONY: logs-auth
logs-auth: ## Логи auth-service
	$(DOCKER_COMPOSE) logs -f auth-service

.PHONY: logs-upload
logs-upload: ## Логи upload-service
	$(DOCKER_COMPOSE) logs -f upload-service

.PHONY: logs-stream
logs-stream: ## Логи stream-service
	$(DOCKER_COMPOSE) logs -f stream-service

.PHONY: logs-processor
logs-processor: ## Логи processor-service
	$(DOCKER_COMPOSE) logs -f processor-service

.PHONY: logs-kafka
logs-kafka: ## Логи Kafka
	$(DOCKER_COMPOSE) logs -f kafka

.PHONY: logs-postgres
logs-postgres: ## Логи PostgreSQL
	$(DOCKER_COMPOSE) logs -f postgres

.PHONY: logs-minio
logs-minio: ## Логи MinIO
	$(DOCKER_COMPOSE) logs -f minio

# ==============================================================================
# СТАТУС И МОНИТОРИНГ
# ==============================================================================

.PHONY: ps
ps: ## Список запущенных контейнеров
	$(DOCKER_COMPOSE) ps

.PHONY: stats
stats: ## Использование ресурсов контейнерами
	docker stats --no-stream

.PHONY: health
health: ## Проверить состояние контейнеров
	@echo "$(GREEN)>>> Проверка healthcheck...$(RESET)"
	@for c in $$(docker ps --format '{{.Names}}'); do \
		status=$$(docker inspect --format='{{.State.Status}}' $$c); \
		health=$$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}' $$c); \
		printf "  %-25s %-10s %s\n" "$$c" "$$status" "$$health"; \
	done

# ==============================================================================
# РАЗРАБОТКА
# ==============================================================================

.PHONY: dev-up
dev-up: ## Запустить только инфраструктуру (без микросервисов)
	@echo "$(GREEN)>>> Запуск инфраструктуры (Postgres, MinIO, Kafka, Redis)...$(RESET)"
	$(DOCKER_COMPOSE) up -d postgres minio redis kafka

.PHONY: dev-down
dev-down: ## Остановить инфраструктуру
	$(DOCKER_COMPOSE) stop postgres minio redis kafka

.PHONY: run-auth
run-auth: ## Запустить auth-service локально через Maven
	cd auth-service && $(MAVEN) spring-boot:run

.PHONY: run-upload
run-upload: ## Запустить upload-service локально через Maven
	cd upload-service && $(MAVEN) spring-boot:run

.PHONY: run-stream
run-stream: ## Запустить stream-service локально через Maven
	cd stream-service && $(MAVEN) spring-boot:run

.PHONY: run-processor
run-processor: ## Запустить processor-service локально через Maven
	cd processor-service && $(MAVEN) spring-boot:run

.PHONY: run-gateway
run-gateway: ## Запустить api-gateway локально через Maven
	cd api-gateway && $(MAVEN) spring-boot:run

# ==============================================================================
# БАЗА ДАННЫЕ / MINIO / KAFKA
# ==============================================================================

.PHONY: db-shell
db-shell: ## Открыть psql в контейнере Postgres
	docker exec -it video-postgres psql -U video_user -d videodb

.PHONY: db-users
db-users: ## Показать всех пользователей
	docker exec -it video-postgres psql -U video_user -d videodb -c "SELECT id, username, email, role FROM users;"

.PHONY: db-videos
db-videos: ## Показать все видео
	docker exec -it video-postgres psql -U video_user -d videodb -c "SELECT id, title, status, file_path, playlist_path, user_id FROM videos;"

.PHONY: db-reset
db-reset: ## Полностью очистить БД (все таблицы будут удалены!)
	@echo "$(RED)>>> Удаление всех данных БД...$(RESET)"
	docker exec -it video-postgres psql -U video_user -d videodb -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

.PHONY: kafka-topics
kafka-topics: ## Показать список топиков Kafka
	docker exec -it video-kafka kafka-topics --list --bootstrap-server localhost:9092

.PHONY: kafka-consume
kafka-consume: ## Читать сообщения из топика video.uploaded
	docker exec -it video-kafka kafka-console-consumer \
		--bootstrap-server localhost:9092 \
		--topic video.uploaded \
		--from-beginning \
		--property print.key=true \
		--property print.value=true

.PHONY: minio-shell
minio-shell: ## Открыть оболочку контейнера MinIO
	docker exec -it video-minio sh

# ==============================================================================
# ТЕСТИРОВАНИЕ
# ==============================================================================

.PHONY: test
test: ## Запустить все тесты
	$(MAVEN) test

.PHONY: test-module
test-module: ## Запустить тесты одного модуля: make test-module MODULE=auth-service
	@if [ -z "$(MODULE)" ]; then \
		echo "$(RED)Укажи модуль: make test-module MODULE=auth-service$(RESET)"; \
		exit 1; \
	fi
	$(MAVEN) test -pl $(MODULE) -am

# ==============================================================================
# УТИЛИТЫ
# ==============================================================================

.PHONY: urls
urls: ## Показать полезные URL сервисов
	@echo ""
	@echo "$(GREEN)Полезные ссылки:$(RESET)"
	@echo "  Frontend:      $(YELLOW)http://localhost:8080/login.html$(RESET)"
	@echo "  API Gateway:   $(YELLOW)http://localhost:8080$(RESET)"
	@echo "  Auth Service:  $(YELLOW)http://localhost:8081$(RESET)"
	@echo "  Upload:        $(YELLOW)http://localhost:8082$(RESET)"
	@echo "  Stream:        $(YELLOW)http://localhost:8083$(RESET)"
	@echo "  Processor:     $(YELLOW)http://localhost:8084$(RESET)"
	@echo "  Frontend:      $(YELLOW)http://localhost:8085$(RESET)"
	@echo "  MinIO Console: $(YELLOW)http://localhost:9001$(RESET)  (minioadmin/minioadmin)"
	@echo ""

.PHONY: clean-all
clean-all: clean ## Очистить target + удалить контейнеры и volumes
	$(DOCKER_COMPOSE) down -v
	docker system prune -f
	@echo "$(GREEN)>>> Полная очистка завершена.$(RESET)"