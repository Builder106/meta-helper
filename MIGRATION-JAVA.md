# MetaHelper Java Migration Plan

This document outlines the strategy for migrating the MetaHelper backend from Python (FastAPI) to Java (Spring Boot).

## Status: COMPLETED ✅

The migration has been fully implemented. The backend now uses Java 26, Gradle 9.7.1, and Spring Boot 4.1.1. Android, shared, and iOS modules retain their existing Java 21-compatible toolchains.

## Motivation

Previously, MetaHelper was a split-ecosystem project: Kotlin on Android, Python on the backend. Moving the backend to Java unifies the project into a strict JVM ecosystem. Additionally, building a Spring Boot REST API for image processing, AI integration, and audio generation is an excellent showcase of enterprise-grade Java skills.

## Target Architecture (Implemented)

- **Framework:** Java 26 + Spring Boot 4.1.1
- **Build Tool:** Gradle 9.7.1 (Kotlin DSL)
- **HTTP/Routing:** Spring Web (Multipart file handling)
- **Deployment:** Dockerized Spring Boot app deployed on Render

## Component Migration Strategy (Completed)

### 1. The Web Layer (`main.py`->`ImageController.java`)

**Current (Python):** FastAPI endpoint handling `multipart/form-data`.
**New (Java):** A `@RestController`with a`@PostMapping("/process-image")`that accepts a`@RequestParam("file") MultipartFile`.

### 2. Gemini Vision (`vision.py`->`VisionService.java`)

**Current (Python):** Uses the `google-generativeai`python SDK to pass the image to`gemini-1.5-flash`.
**New (Java):** Uses Spring's `RestClient` to make a direct REST call to the Gemini API (`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent`). The REST approach is lighter than pulling in the full Google Cloud SDK when using API keys rather than GCP service accounts.

### 3. Text-to-Speech (`tts.py`->`TtsService.java`)

**Current (Python):** Uses the `edge-tts` Python package (an unofficial wrapper for Microsoft Edge's Read Aloud API) to get free, high-quality TTS.
**New (Java):** Uses the official Azure Speech Java SDK directly and requests MP3 output. No Python runtime is required.

### 4. Audio Processing (`audio.py`->`AudioService.java`)

**Current (Python):** Uses `pydub`, which shells out to `ffmpeg`, to scale the audio amplitude (gain control) so it doesn't blast the user's ears.
**New (Java):** Uses Java's `ProcessBuilder`to run`ffmpeg` directly.
`ffmpeg -i input.mp3 -filter:a "volume=0.1" output.mp3`
This removes the need for a middleman library and keeps the audio processing fast and native.

## Completed Implementation

### Files Created/Modified

- `backend/build.gradle.kts` - Spring Boot 4.1.1, Java 26, Lombok
- `backend/settings.gradle.kts` - Project settings
- `backend/src/main/java/com/metahelper/MetaHelperApplication.java` - Main entry point
- `backend/src/main/java/com/metahelper/controller/ImageController.java` - REST endpoint
- `backend/src/main/java/com/metahelper/service/VisionService.java` - Gemini Vision integration
- `backend/src/main/java/com/metahelper/service/TtsService.java` - Azure Speech Java SDK
- `backend/src/main/java/com/metahelper/service/AudioService.java` - ffmpeg via ProcessBuilder
- `backend/src/main/resources/application.properties` - Configuration
- `backend/.env.example` - Environment template
- `backend/Dockerfile` - Multi-stage Java build with ffmpeg
- `backend/.dockerignore` - Updated for Java
- `backend/src/test/java/com/metahelper/...` - Unit tests for all services

### Documentation Updated

- `README.md` - Updated to Java backend, added iOS/Compose Multiplatform badge, updated project structure
- `CONTRIBUTING.md` - Updated all setup instructions for Java backend + iOS
- `.github/workflows/ci.yml` - Added shared and iOS jobs, updated comments
- `.gitignore` - Added iOS/Xcode and Java artifacts
- `MIGRATION-JAVA.md` - This file (marked complete)

### Backwards Compatibility

- The API contract (`POST /process-image`returning`audio/mpeg`) is identical
- Android client requires zero changes
- Environment variables are the same (`GOOGLE_API_KEY`, `AZURE_SPEECH_KEY`, `AZURE_SPEECH_REGION`, `AZURE_SPEECH_VOICE`, `GEMINI_MODEL`, and `AUDIO_AMPLITUDE_MULTIPLIER`)

## Verification

The repository documents the Java 26 baseline and the commands below, but this documentation update does not claim that tests or builds passed.

```bash

# Build and run tests

cd backend
./gradlew test

# Run locally

./gradlew bootRun

# Docker build

docker build -t metahelper-backend ./backend
docker run -p 8080:8080 --env-file backend/.env metahelper-backend
```

## Future Considerations

1. **Speech provider**: Azure Speech is the current Java-native TTS provider; revisit it if voice quality, quotas, or cost requirements change.
2. **Google Cloud SDK**: If migrating to GCP service accounts, consider `google-cloud-vertexai` Java SDK.
3. **Performance**: The Azure SDK returns MP3 bytes directly; continue monitoring synthesis latency in production.
