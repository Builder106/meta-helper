<picture>
  <source media="(prefers-color-scheme: dark)"  srcset="assets/banner-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="assets/banner-light.svg">
  <img alt="MetaHelper — hear the code in front of you, read aloud through your Meta Ray-Ban glasses" src="assets/banner-dark.svg">
</picture>

[![CI](https://github.com/Builder106/MetaHelper/actions/workflows/ci.yml/badge.svg)](https://github.com/Builder106/MetaHelper/actions/workflows/ci.yml)
[![Python](https://img.shields.io/badge/python-3.13%2B-blue.svg)](https://www.python.org/)
[![Kotlin / Android](https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](#license)
[![Backend: live](https://img.shields.io/badge/backend-live-success.svg)](https://metahelper.onrender.com)

**Hear the code in front of you** — an audio-first programming assistant for Meta Ray-Ban glasses.

MetaHelper reads code, error messages, and technical text aloud through your Meta Ray-Ban glasses — an audio-first way to access code that isn't already digital text on your device. Look at code on a screen, whiteboard, projector, or printed page and take a photo; MetaHelper reads it back **verbatim** (so you can follow or transcribe it) and explains what it does in plain English, via Gemini Vision — not a generic "describe my surroundings" caption. It's aimed at developers and CS students who are blind or have low vision, and anyone who needs hands-free, audio access to code in the world around them.

> **Status:** the vision prompt is currently tuned for C programming; generalizing it to any language is the next step toward the accessibility-first goal above. **Demo:** coming soon.

## How it works

When you take a photo on the glasses, it lands in the phone's gallery. MetaHelper's Android app watches for that new photo, ships it to the backend, and plays the spoken solution that comes back. Double-tap the glasses to replay the last answer.

```mermaid
sequenceDiagram
    actor User as User (glasses)
    participant GW as Android · GalleryWatcher
    participant GM as Android · GlassesManager
    participant API as Android · ApiClient
    participant BE as Backend · FastAPI
    participant V as vision.py · Gemini
    participant T as tts.py · edge-tts
    participant A as audio.py · pydub
    participant AP as Android · AudioPlayer

    User->>GW: Take photo of a coding problem
    GW->>GM: New gallery photo detected (MediaStore)
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

> **Capture note:** Photo capture currently works through `GalleryWatcher`, a `MediaStore` `ContentObserver` that detects new glasses photos saved to the phone gallery. The Meta Wearables SDK's direct-capture path (`StreamSession`) is stubbed/in-progress and is the intended future approach.

## Project structure

```
MetaHelper/
├── backend/   Python 3.13 · FastAPI — vision → TTS → audio pipeline
│   └── app/
│       ├── main.py             GET / (health), POST /process-image
│       └── services/
│           ├── vision.py       Gemini (gemini-3-pro-preview) — reads & solves the problem
│           ├── tts.py          edge-tts (en-US-GuyNeural + fallbacks)
│           └── audio.py        pydub playback-gain scaling
└── android/   Kotlin · Jetpack Compose — Meta Wearables SDK client
    └── app/src/main/kotlin/com/metahelper/app/
        ├── GalleryWatcher.kt   Detects new glasses photos via MediaStore
        ├── GlassesManager.kt   Reads photo bytes, drives the flow
        ├── ApiClient.kt        multipart POST /process-image
        └── AudioPlayer.kt      Plays the returned MP3 (double-tap to replay)
```

## Backend — setup

Requires Python 3.13+ and `ffmpeg` (used by `pydub` for audio export).

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Copy `backend/.env.example` to `backend/.env` and fill in your values:

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `GOOGLE_API_KEY` | yes | — | Google Gemini API key ([create one](https://aistudio.google.com/apikey)) |
| `AUDIO_AMPLITUDE_MULTIPLIER` | no | `0.1` | Playback gain (0.0–1.0); lower keeps audio from overpowering the glasses' speakers |

**API**

| Method | Route | Body | Returns |
|---|---|---|---|
| `GET` | `/` | — | JSON health check |
| `POST` | `/process-image` | multipart form, field `file` (image) | `audio/mpeg` MP3 bytes |

**Tests**

```bash
cd backend
python -m pytest
```

(Tests live in `backend/tests/`; config in `backend/pytest.ini`.)

## Android — setup

Requires the Gradle wrapper (included: `./gradlew`), AGP 8.13.2, Kotlin 2.1.0; targets `compileSdk 36`, `minSdk 29`, `targetSdk 34`.

```bash
cd android
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
```

**Meta Wearables SDK access (required).** The app depends on the Meta Wearables SDK (`com.meta.wearable:mwdat-core` / `mwdat-camera` `0.3.0`), which is published to **GitHub Packages** at `https://maven.pkg.github.com/facebook/meta-wearables-dat-android`. GitHub Packages requires authentication even for read access, so you must supply a **GitHub Personal Access Token with the `read:packages` scope** or Gradle cannot resolve the SDK and the build will fail.

Provide the token one of two ways:

- Add it to `android/local.properties` (this file is git-ignored — do **not** commit it):

  ```properties
  github_token=ghp_yourTokenWithReadPackagesScope
  ```

- Or export it as an environment variable before building:

  ```bash
  export GITHUB_TOKEN=ghp_yourTokenWithReadPackagesScope
  ```

On sync, the build log prints `SUCCESS: github_token loaded (...)` when the token is found, or an `ERROR: github_token NOT FOUND` message when it is missing.

Point the app's `ApiClient` at your backend — the live instance at `https://metahelper.onrender.com`, or your own local/self-hosted server.

## Self-hosting

The backend ships with a `Dockerfile` (Python 3.13 slim, `ffmpeg` baked in):

```bash
docker build -t metahelper-backend ./backend
docker run -p 8000:8000 --env-file backend/.env metahelper-backend
```

The hosted backend at **https://metahelper.onrender.com** is deployed on [Render](https://render.com). Free-tier instances sleep when idle, so the first request after a quiet period may take a few seconds to wake.

## License

MetaHelper is released under the MIT License. See [LICENSE](./LICENSE) for the full text.
