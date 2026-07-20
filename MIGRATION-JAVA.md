# MetaHelper Java Migration Plan

This document outlines the strategy for migrating the MetaHelper backend from Python (FastAPI) to Java (Spring Boot). 

## Motivation
Currently, MetaHelper is a split-ecosystem project: Kotlin on Android, Python on the backend. Moving the backend to Java unifies the project into a strict JVM ecosystem. Additionally, building a Spring Boot REST API for image processing, AI integration, and audio generation is an excellent showcase of enterprise-grade Java skills.

## Target Architecture

- **Framework:** Java 21 + Spring Boot 3.x
- **Build Tool:** Gradle (Kotlin DSL) or Maven
- **HTTP/Routing:** Spring Web (Multipart file handling)
- **Deployment:** Dockerized Spring Boot app deployed on Render

## Component Migration Strategy

### 1. The Web Layer (`main.py` -> `ImageController.java`)
**Current (Python):** FastAPI endpoint handling `multipart/form-data`.
**New (Java):** A `@RestController` with a `@PostMapping("/process-image")` that accepts a `@RequestParam("file") MultipartFile`.

### 2. Gemini Vision (`vision.py` -> `VisionService.java`)
**Current (Python):** Uses the `google-generativeai` python SDK to pass the image to `gemini-3-pro-preview`.
**New (Java):** 
- We can use the official `google-cloud-vertexai` Java SDK, OR
- Simply use Spring's `RestClient` or `WebClient` to make a direct REST call to the Gemini API (`https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent`). The REST approach is often lighter than pulling in the full Google Cloud SDK if we are just using API keys rather than GCP service accounts.

### 3. Text-to-Speech (`tts.py` -> `TtsService.java`)
**Current (Python):** Uses the `edge-tts` Python package (an unofficial wrapper for Microsoft Edge's Read Aloud API) to get free, high-quality TTS.
**New (Java):**
Since `edge-tts` is unique to Python, we have three choices:
1. **(Recommended for ease):** Shell out to the Python `edge-tts` CLI from Java using `ProcessBuilder`. Since we already bake `ffmpeg` into our Docker image, we can just ensure Python/edge-tts is also installed in the Dockerfile.
2. **(Pure Java, Paid):** Use the official Azure Cognitive Services Java SDK or Google Cloud TTS Java SDK.
3. **(Pure Java, Free but complex):** Reverse engineer the websocket protocol `edge-tts` uses and implement it in Java using a library like `Java-WebSocket`.

### 4. Audio Processing (`audio.py` -> `AudioService.java`)
**Current (Python):** Uses `pydub`, which shells out to `ffmpeg`, to scale the audio amplitude (gain control) so it doesn't blast the user's ears.
**New (Java):**
Use Java's `ProcessBuilder` to run `ffmpeg` directly. 
`ffmpeg -i input.mp3 -filter:a "volume=0.1" output.mp3`
This removes the need for a middleman library and keeps the audio processing fast and native.

## Step-by-Step Execution Plan

1. **Bootstrap:** Use Spring Initializr to generate a Java 21 project with `Spring Web` and `Lombok`.
2. **Docker Setup:** Update the `Dockerfile` to use a JDK base image (e.g., `eclipse-temurin:21`), but ensure `ffmpeg` and `python3-pip` (for `edge-tts`) are installed via `apt-get`.
3. **Implement Services:**
   - Create `VisionService` to handle the Gemini REST call.
   - Create `TtsService` to invoke `edge-tts`.
   - Create `AudioService` to invoke `ffmpeg`.
4. **Wire Controller:** Build the `/process-image` endpoint to string these services together.
5. **Testing:** Write `@SpringBootTest` integration tests that mock the Gemini API and verify the audio byte response.
6. **Swap Android Client:** Update the Android `ApiClient` base URL if testing locally, otherwise the Android code requires zero changes (the API contract remains identical).
