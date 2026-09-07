# Self-hosted deployment

MetaHelper's backend is a Docker image. The supported hosted deployment uses
[Coolify](https://coolify.io/), an open-source self-hosted platform. Coolify
provides the application lifecycle, reverse proxy, and HTTPS certificate
management; the backend itself remains portable Docker Compose.

## Prerequisites

- An Ubuntu LTS server with Docker and enough memory for the Java 26 runtime.
- A domain or subdomain whose DNS `A` or `AAAA` record points to that server.
- A Coolify installation. Follow the [official installation guide](https://coolify.io/docs/get-started/installation/).
- The `GOOGLE_API_KEY`, `AZURE_SPEECH_KEY`, and `AZURE_SPEECH_REGION` values.

The current shared host keeps its existing nginx service on ports 80 and 443.
Coolify's dashboard and realtime ports are therefore bound to localhost. To
open the dashboard from a development machine, create an SSH tunnel:

```bash
ssh -N \\
  -L 8000:127.0.0.1:8000 \\
  -L 6001:127.0.0.1:6001 \\
  -L 6002:127.0.0.1:6002 \\
  your-coolify-host
```

Then open `http://localhost:8000` and create the first administrator account.
Public routing for deployed applications still requires an explicit nginx and
DNS configuration, so installing Coolify alone does not change the live
MetaHelper endpoint.

## Create the Coolify resource

1. Connect the `Builder106/meta-helper` GitHub repository to Coolify.
2. Create a Docker Compose resource for the repository.
3. Set the Compose file to `deploy/compose.yaml`.
4. Assign the public domain to `metahelper-backend` on container port `8080`.
5. Enable HTTPS and configure the environment variables listed below.
6. Deploy and confirm that `https://your-domain.example/` returns the JSON health response.

The compose file uses the prebuilt image published to GHCR by
`.github/workflows/docker-publish.yml`. If the package is private, configure
GHCR credentials in Coolify rather than adding a token to this repository.

Required environment variables:

| Variable | Purpose |
| --- | --- |
| `GOOGLE_API_KEY` | Gemini Vision access |
| `AZURE_SPEECH_KEY` | Azure Speech access |
| `AZURE_SPEECH_REGION` | Azure Speech resource region |

Optional variables and their defaults are defined in
[`deploy/compose.yaml`](../deploy/compose.yaml) and
[`backend/.env.example`](../backend/.env.example).

## Automatic deployment

The container workflow runs after the `CI` workflow succeeds on `main`. It
publishes `ghcr.io/builder106/meta-helper:latest`, then calls Coolify to pull
and restart the service.

Add these repository secrets after the Coolify resource is working:

- `COOLIFY_WEBHOOK`: the resource's manual deployment webhook URL.
- `COOLIFY_TOKEN`: the bearer token accepted by that webhook.

Until both secrets exist, the image is still published but the Coolify step is
reported as skipped. See Coolify's [GitHub Actions guide](https://coolify.io/docs/applications/ci-cd/github/actions/) for the webhook setup.

## Mobile clients

The mobile clients no longer contain a hosted-provider URL.

For Android, pass the public backend URL when building the app:

```bash
cd android
./gradlew assembleDebug -Pmetahelper.backend.url=https://your-domain.example
```

For iOS, set `MetaHelperBackendURL` in
[`iosApp/src/iosMain/resources/Info.plist`](../iosApp/src/iosMain/resources/Info.plist)
to the public HTTPS URL before creating the app archive. The checked-in
`localhost` value is intended for local development only.

Keep the old service available until a client build using the new URL has
passed a health check and a real `/process-image` request. Do not remove the
old service or change DNS as part of a code-only deployment.

## Standalone Docker Compose

Coolify is the recommended hosted path, but the same compose file can run on
any Docker host:

```bash
docker compose --env-file backend/.env -f deploy/compose.yaml up -d
```

This exposes port `8080` on loopback by default. Put a FOSS reverse proxy in
front of it, or set `METAHELPER_BIND_ADDRESS=0.0.0.0` for a controlled
internal network.
