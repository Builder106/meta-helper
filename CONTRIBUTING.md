# Contributing to MetaHelper

MetaHelper is an audio-first programming assistant for Meta Ray-Ban glasses: you look at code on a screen, whiteboard, or page, take a photo, and MetaHelper reads it back verbatim and explains it aloud — built with blind and low-vision developers in mind.

Thanks for your interest in contributing. This guide covers how to set up both halves of the project, how to build and test them, the guardrails to keep in mind, and how to get a change merged.

## Repository layout

| Path | What it is |
| --- | --- |
| `backend/` | Python 3.13 FastAPI service. Gemini Vision reads the problem, edge-tts synthesizes speech, pydub adjusts gain, and the endpoint returns MP3 bytes. |
| `android/` | Kotlin + Jetpack Compose app using the Meta Wearables SDK. Detects glasses photos, posts them to the backend, and plays the spoken response. |
| `assets/` | Shared brand assets (banner, logo) referenced by the README. |
| `.github/workflows/ci.yml` | Continuous integration workflow. |

### How a request flows

```
Glasses photo
  → Android GalleryWatcher detects the new photo in the phone gallery
  → GlassesManager reads the image bytes
  → ApiClient sends a multipart POST to /process-image
  → Backend: Gemini Vision reads the problem
  → edge-tts synthesizes speech → pydub applies gain → returns MP3
  → Android AudioPlayer plays it (double-tap the glasses to replay)
```

## Backend setup (`backend/`)

Requires Python 3.13 and `ffmpeg` (pydub needs it for audio processing).

```bash
cd backend
cp .env.example .env          # then fill in GOOGLE_API_KEY
uv sync --group dev
uv run uvicorn app.main:app --reload
```

Environment variables (see `backend/.env.example`):

- `GOOGLE_API_KEY` — **required**. A Google Gemini API key (create one at https://aistudio.google.com/apikey). Without it the vision service fails.
- `AUDIO_AMPLITUDE_MULTIPLIER` — optional, defaults to `0.1`. Playback gain (0.0–1.0) applied to the synthesized speech so it doesn't overpower the glasses' speakers.

### Backend endpoints

- `GET /` — health check. Returns `{"message": "MetaHelper API is running"}`.
- `POST /process-image` — accepts a multipart upload with form field `file` (a JPEG image) and returns `audio/mpeg` MP3 bytes.

### Backend tests

```bash
cd backend
python -m pytest
```

Tests live in `backend/tests/`; pytest config is in `backend/pytest.ini`.

### Backend with Docker

```bash
docker build -t metahelper-backend ./backend
docker run -p 8000:8000 --env-file backend/.env metahelper-backend
```

The deployed backend lives at https://metahelper.onrender.com (Render).

## Android setup (`android/`)

Toolchain: Gradle 8.13 (use the bundled `./gradlew` wrapper), Android Gradle Plugin 8.13.2, Kotlin 2.1.0, Jetpack Compose. `compileSdk 36`, `minSdk 29`, `targetSdk 34`. Application id `com.metahelper.app`.

### Meta Wearables SDK — GitHub token required

The Meta Wearables SDK (`com.meta.wearable:mwdat-core` / `mwdat-camera`, version `0.3.0`) is **not** on Maven Central. It is resolved from GitHub Packages at:

```
https://maven.pkg.github.com/facebook/meta-wearables-dat-android
```

GitHub Packages requires authentication, so you must supply a GitHub personal access token with the **`read:packages`** scope. Without it the Gradle sync cannot resolve the SDK and the Android build fails.

Provide the token one of two ways:

1. Add it to `android/local.properties` (this file is gitignored — never commit it):

   ```properties
   github_token=ghp_yourTokenWithReadPackagesScope
   ```

2. Or export it as an environment variable:

   ```bash
   export GITHUB_TOKEN=ghp_yourTokenWithReadPackagesScope
   ```

On sync, `settings.gradle.kts` logs whether the token was found, so check the build output if resolution fails.

### Android build and test

```bash
cd android
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
```

### Photo-capture flow (important context)

Photo capture currently works through `GalleryWatcher`, a `MediaStore` `ContentObserver` that detects new glasses photos as they are saved to the phone's gallery. This is a deliberate workaround: the Meta Wearables SDK's `StreamSession` direct-capture path is stubbed/in progress. When contributing here, document and target the **real** `GalleryWatcher` flow — but note that the SDK direct-capture path is the intended/future approach, and changes that move us toward it are welcome (just keep the existing flow working until the SDK path is reliable).

## Guardrails

Please respect these project-specific constraints:

- **Keep secrets out of git.** `android/local.properties` holds the `github_token` and `backend/.env` holds `GOOGLE_API_KEY`; both are gitignored and must stay that way. Never paste tokens or API keys into source files, tests, commit messages, or PR descriptions. If a secret is ever committed, treat it as compromised and rotate it.
- **The Gemini prompt is intentionally code-focused.** The prompt in `backend/app/services/vision.py` is tuned to read and explain code and technical content aloud (any language), including the dual-layer "read it verbatim, then explain it" audio format. Do not relax it into a generic "describe my surroundings" prompt. Prompt changes are fine, but keep MetaHelper a code assistant, not a general scene describer.
- **The GalleryWatcher capture path is a known workaround.** Don't delete it in favor of the SDK `StreamSession` path until that path is actually working end-to-end. Document the real behavior, not the aspirational one.
- **Keep audio TTS-friendly.** Backend output is read aloud by TTS, so avoid LaTeX, markdown, or notation that doesn't speak well — favor plain spoken English.

## Commit messages

- Write an imperative subject line: "Add gallery debounce", not "Added" or "Adds".
- Keep the subject under ~72 characters; use the body to explain the *why* when it isn't obvious.
- One logical change per commit where practical.
- **Do not add `Co-Authored-By` trailers** (or any AI/tool attribution trailers). Commits should attribute authorship to the human contributor only.

## Pull request process

1. Branch off `main` (e.g. `git checkout -b fix-gallery-debounce`).
2. Make your change, keeping the backend and Android pieces consistent with the data flow above.
3. Run the relevant tests locally before pushing (`python -m pytest` for backend changes, `./gradlew testDebugUnitTest` for Android changes).
4. Open a PR against `main` at https://github.com/Builder106/MetaHelper with a clear description of what changed and why.
5. CI (`.github/workflows/ci.yml`) must pass before a PR can be merged. Address any failures rather than disabling checks.

## Out of scope

To keep the project focused, the following changes will generally be declined unless discussed in an issue first:

- **Adding unrelated AI providers.** MetaHelper uses Gemini for vision and edge-tts for speech. Don't swap in or bolt on additional LLM/vision/TTS providers without prior agreement.
- **Broadening beyond the programming-assistant scope.** General object recognition, scene description, translation, OCR-for-anything, or other "look at the world" features are out of scope. The product is "read and explain code aloud."
- **Removing the GalleryWatcher path** before the SDK `StreamSession` direct-capture path is proven to work.
- **Committing secrets, build artifacts, or generated files** (APKs, `.env`, `local.properties`, `__pycache__/`, `build/`).

If you have an idea that touches one of these areas, open an issue to discuss it before writing code.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
