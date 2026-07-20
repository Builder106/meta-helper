FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update && apt-get install -y \
    ffmpeg \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install edge-tts --break-system-packages

WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle
COPY . .

RUN ./gradlew build -x test

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "build/libs/metahelper-backend-0.0.1-SNAPSHOT.jar"]
