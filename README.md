<picture>
  <source media="(prefers-color-scheme: dark)"  srcset="assets/banner-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="assets/banner-light.svg">
  <img alt="MetaHelper — hear the code in front of you, read aloud through your Meta Ray-Ban glasses" src="assets/banner-dark.svg">
</picture>

[![CI](https://github.com/Builder106/meta-helper/actions/workflows/ci.yml/badge.svg)](https://github.com/Builder106/meta-helper/actions/workflows/ci.yml)
[![Python](https://img.shields.io/badge/python-3.13%2B-blue.svg)](https://www.python.org/)
[![Kotlin / Android](https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![iOS / Compose Multiplatform](https://img.shields.io/badge/iOS-Compose%20Multiplatform-000000.svg?logo=apple&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](#license)
[![Backend: live](https://img.shields.io/badge/backend-live-success.svg)](https://metahelper.onrender.com)

> **Hands-free audio programming assistant for Meta Ray-Ban smart glasses.** Snap a photo of code on a screen, whiteboard, or paper, and hear the explanation spoken directly into your ears.

## 💡 What is MetaHelper?

Reading code on physical whiteboards, presentation slides, or printed handouts can be difficult for developers with visual impairments or when working hands-free.

MetaHelper turns Meta Ray-Ban smart glasses into an audio coding companion. When you capture a photo of code, the app reads the syntax verbatim, identifies syntax errors or logic bugs using Gemini Vision AI, and speaks a clear explanation directly through the open-ear glasses speakers.

**Backend status:** Live on Render ([metahelper.onrender.com](https://metahelper.onrender.com))

## How it works

When you take a photo with your glasses, the photo syncs to your phone. MetaHelper's mobile companion app detects the new image, sends it to the cloud vision engine, and speaks the solution through the glasses. Double-tapping the glasses stem replays the audio.

```mermaid
sequenceDiagram
    actor User as User (glasses)
    participant GW as Android · GalleryWatcher / iOS · PhotosObserver
    participant GM as Android/iOS · GlassesManager
    participant API as Android/iOS · ApiClient
    participant BE as Backend · Spring Boot (Java)
    participant V as vision.py · Gemini
    participant T as tts.py · edge-tts
    participant A as audio.py · pydub / ffmpeg
    participant AP as Android/iOS · AudioPlayer

    User->>GW: Take photo of a coding problem
    GW->>GM: New gallery photo detected (MediaStore / Photos framework)
    GM->>API: Read image bytes
    API->>BE: multipart POST /process-image (file)
    BE->>V: Read & solve the problem (gemini-3-pro-preview)
    V-->>BE: Solution text (verbatim + narrative)
    BE->>T: Synthesize speech (en-US-GuyNeural)
    T-->>BE: MP3 audio
    BE->>A: Scale playback gain
    A-->>BE: Quieted MP3
    BE-->>API: 200 · audio/mpeg (MP3 bytes)
    API->>AP: Hand off audio
    AP-->>User: Speak the solution
    User->>AP: Double-tap glasses to replay
```

> **Capture note:**Photo capture currently works through**gallery polling** — the glasses take the photo through Meta AI natively and the app reads it from the phone gallery (`READ_MEDIA_IMAGES` on Android, Photos framework on iOS). The Meta Wearables SDK's direct-capture path (`StreamSession`on Android,`MWDAT` on iOS) is stubbed/in-progress and is the intended future approach.

## Project structure

```text
MetaHelper/
├── backend/   Java 21 · Spring Boot 3 — vision → TTS → audio pipeline
│   └── app/
│       ├── main.py             GET / (health), POST /process-image
│       └── services/
│           ├── vision.py       Gemini (gemini-3-pro-preview) — reads & solves the problem
│           ├── tts.py          edge-tts (en-US-GuyNeural + fallbacks)
│           └── audio.py        pydub playback-gain scaling
├── shared/    Kotlin Multiplatform — shared business logic
│   └── src/
│       ├── commonMain/kotlin/com/metahelper/shared/
│       │   ├── GlassesManager.kt     Core flow coordinator
│       │   ├── ApiClient.kt          Platform-agnostic HTTP client
│       │   ├── GalleryWatcher.kt     expect/actual for photo detection
│       │   ├── AudioPlayer.kt        expect/actual for audio playback
│       │   ├── VolumeController.kt   expect/actual for volume control
│       │   └── WearablesConnectionMonitor.kt  expect/actual for SDK connection
│       ├── androidMain/...           Android implementations (MediaStore, MediaPlayer, etc.)
│       └── iosMain/...               iOS implementations (Photos, AVFoundation, MWDAT)
├── android/   Kotlin · Jetpack Compose — Meta Wearables SDK client
│   └── app/src/main/kotlin/com/metahelper/app/
│       ├── GalleryWatcher.kt   Detects new glasses photos via MediaStore
│       ├── GlassesManager.kt   Reads photo bytes, drives the flow
│       ├── ApiClient.kt        multipart POST /process-image
│       └── AudioPlayer.kt      Plays the returned MP3 (double-tap to replay)
├── iosApp/    iOS · Compose Multiplatform — shared UI + iOS platform code
└── assets/    Shared brand assets (banner, logo) referenced by the README
```

## Backend — setup

Requires Java 21 and `ffmpeg` (used for audio export).

```bash
cd backend
./gradlew bootRun
```

Copy `backend/.env.example`to`backend/.env` and fill in your values:

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `GOOGLE_API_KEY` | yes | — | Google Gemini API key ([create one](https://aistudio.google.com/apikey)) |
| `AUDIO_AMPLITUDE_MULTIPLIER` | no | `0.1` | Playback gain (0.0–1.0); lower keeps audio from overpowering the glasses' speakers |

## API

| Method | Route | Body | Returns |
| --- | --- | --- | --- |
| `GET` | `/` | — | JSON health check |
| `POST` | `/process-image` | multipart form, field`file`(image) | `audio/mpeg` MP3 bytes |

## Tests

```bash
cd backend
./gradlew test
```

(Tests live in `backend/src/test/`.)

## Android — setup

Requires the Gradle wrapper (included: `./gradlew`), AGP 8.13.2, Kotlin 2.4.10; targets `compileSdk 36`, `minSdk 29`, `targetSdk 34`.

```bash
cd android
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
```

**Meta Wearables SDK access (required).**The app depends on the Meta Wearables SDK (`com.meta.wearable:mwdat-core`/`mwdat-camera` `0.3.0`), which is published to**GitHub Packages**at `https://maven.pkg.github.com/facebook/meta-wearables-dat-android`. GitHub Packages requires authentication even for read access, so you must supply a**GitHub Personal Access Token with the `read:packages` scope** or Gradle cannot resolve the SDK and the build will fail.

Provide the token one of two ways:

- Add it to `android/local.properties` (this file is git-ignored — do **not** commit it):

  ```properties
  github_token=ghp_yourTokenWithReadPackagesScope
  ```

- Or export it as an environment variable before building:

  ```bash
  export GITHUB_TOKEN=ghp_yourTokenWithReadPackagesScope
  ```

On sync, the build log prints `SUCCESS: github_token loaded (...)`when the token is found, or an`ERROR: github_token NOT FOUND` message when it is missing.

Point the app's `ApiClient`at your backend — the live instance at`https://metahelper.onrender.com`, or your own local/self-hosted server.

## Self-hosting

The backend ships with a `Dockerfile`(Java 21,`ffmpeg` baked in):

```bash
docker build -t metahelper-backend ./backend
docker run -p 8000:8000 --env-file backend/.env metahelper-backend
```

The hosted backend at **<https://metahelper.onrender.com>** is deployed on [Render](https://render.com). Free-tier instances sleep when idle, so the first request after a quiet period may take a few seconds to wake.

## License

MetaHelper is released under the MIT License. See [LICENSE](./LICENSE) for the full text.
