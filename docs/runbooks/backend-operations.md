# Backend operations

## Check service health

The backend exposes `GET /` with a JSON health response. On a local instance,
run `curl --fail http://localhost:8080/`. The response confirms the API is up;
it does not validate external Gemini or Azure credentials. The image processing
route is `POST /process-image`, using a multipart image field named `file`,
and returns audio/mpeg MP3 bytes. See `docs/deployment.md` for hosting details.

## Check speech configuration

Azure speech synthesis requires AZURE_SPEECH_KEY and AZURE_SPEECH_REGION in the
backend environment. Configure them on the host without printing their values
or committing an .env file. Review `backend/.env.example` for optional voice
and audio gain settings. After changing configuration, restart
the backend through the deployment host and check the image-to-audio flow.

## Check vision configuration

Gemini vision requires GOOGLE_API_KEY in the backend environment. GEMINI_MODEL
can select the model. Check that configuration is supplied to the backend
process without logging or copying secret values into an issue. A health
response alone does not prove vision credentials work. See
`backend/.env.example` and `docs/deployment.md` for configuration and hosting.

## Investigate missing glasses photos

The shipping capture path uses gallery polling. Confirm that the glasses photo
has synced into the phone gallery and the companion app has gallery permission.
Android GalleryWatcher detects new photos; the backend processes uploaded bytes.
If the backend health check passes but no upload arrives, inspect the phone's
gallery watcher and permissions before investigating speech or vision services.
