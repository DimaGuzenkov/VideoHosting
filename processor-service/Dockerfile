FROM eclipse-temurin:17-jre-alpine

# Устанавливаем ffmpeg
RUN apk add --no-cache ffmpeg

WORKDIR /app

# JAR монтируется через volume, но ENTRYPOINT нужен
ENTRYPOINT ["java", "-jar", "/app.jar"]