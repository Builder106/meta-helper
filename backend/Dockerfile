# Build stage
FROM eclipse-temurin:26-jdk-jammy AS builder

WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:26-jre-jammy

RUN apt-get update && apt-get install -y \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown -R appuser:appuser /app

COPY --from=builder /app/build/libs/app.jar app.jar

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
