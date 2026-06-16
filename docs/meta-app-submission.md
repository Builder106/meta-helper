# Meta Wearables — App Registration & Submission Notes

Working notes for registering MetaHelper in the **Meta Wearables Developer Center**
(Path C: distributing to testers / publishing), and the permission justification to
submit. Sourced from Meta's developer docs and the `facebook/meta-wearables-dat-android`
repo as of 2026-06-16. Treat portal labels as approximate — the Developer Center is
login-gated, so confirm exact wording in the console.

> **You only need this for distribution.** A single end-to-end test on your own paired
> glasses needs nothing here — just **Developer Mode** in the Meta AI app and the
> `APPLICATION_ID` set as a string (`"\0"`). See the project README / handoff notes.

## Product positioning (use this framing throughout)

MetaHelper reads and explains code, error messages, and technical text aloud through
Meta Ray-Ban glasses — an audio-first way to access code that isn't already digital text
on your device. **Primary audience: developers and CS students who are blind or have low
vision**, plus anyone needing hands-free audio access to code on a screen, whiteboard,
projector, or printed page. The accessibility framing is also the strongest permission
justification (see below) and the kind Meta's review is set up to approve.

## Access status (developer preview)

- The Device Access Toolkit is in **public developer preview** — openly downloadable, no
  individual waitlist to build/test.
- **Publishing to the general public is gated** to "select partners" during preview;
  general availability is planned for **2026**. In preview you can create a **release
  channel** and invite named testers, but not ship to the public.
- The **Developer Center (project registration, real Application ID) is limited to
  countries where the AI glasses are supported.** Confirm by attempting signup.
- **Voice invocation / Meta AI commands are NOT in the preview** — camera, microphone,
  and speaker only. (MetaHelper uses none of the mic path.)

## Prerequisites

- **Meta AI app** (`com.facebook.stella`) **v254+** on the phone, glasses paired through it.
- Supported glasses (Ray-Ban Meta Gen 1/2, Oakley Meta, Meta Ray-Ban Display) on current
  firmware; **Android 10+** phone.
- **Developer Mode** enabled in the Meta AI app: tap the app version number 5× to reveal
  the toggle, enable it for the linked glasses. (May reset after app/firmware updates.)
- A **GitHub personal access token (classic) with `read:packages`** to pull the SDK from
  GitHub Packages (`maven.pkg.github.com/facebook/meta-wearables-dat-android`) — already
  wired in this repo via `github_token` in `local.properties`.

## Registration steps (Path C)

1. Sign in at **https://wearables.developer.meta.com/** with a **Meta Managed Account**;
   set up your organization.
   - Gotcha: being signed into `developers.meta.com` can break `wearables.developer.meta.com`
     links (note `developer` vs `developers`). Sign out of the former first.
2. **New project** → name + description.
3. **Configuration** tab → add the Android app: **package name** (`com.metahelper.app`) and
   the **app-signing certificate hash**. The portal then shows the assigned
   **Application ID** *and* a **CLIENT_TOKEN**.
4. **Permissions** tab → request the access the app needs, with the justification below.
5. **Release Channel** (e.g. "Internal") → assign an app version, invite testers by their
   Meta-account email (testers need a pre-existing meta.ai account).

## Manifest changes for the real credentials

Mirror the official `CameraAccess` sample — feed values via Gradle placeholders rather than
hardcoding:

```xml
<meta-data android:name="com.meta.wearable.mwdat.APPLICATION_ID" android:value="${mwdat_application_id}" />
<meta-data android:name="com.meta.wearable.mwdat.CLIENT_TOKEN"   android:value="${mwdat_client_token}" />
```

supplied from `local.properties` (alongside `github_token`). **Critical gotcha (repo
issue #65):** a bare numeric `android:value="0"` (and real Application IDs are all-numeric)
is parsed as an Integer, so the SDK's `Bundle.getString()` returns null and silently
ignores it. **Escape it as a string** (`"\0"`) or use a string resource — otherwise the ID
is "not recognized." MetaHelper's manifest does not yet declare `CLIENT_TOKEN`; add it for
the attestation/production path.

## Permission justification to submit

**Important nuance:** MetaHelper currently captures via **gallery polling** — the glasses
take the photo through Meta AI natively and the app reads it from the Android gallery
(`READ_MEDIA_IMAGES`). It does **not** call the DAT camera/StreamSession API, so it may not
require the DAT **camera** permission at all — only DAT registration (connection state).
Confirm against Meta's policy; "no special capture permission needed" makes review easier.
The justification below applies **if/when** the SDK direct-capture path is revived.

> MetaHelper is an accessibility application that reads and explains code and technical
> content aloud for developers and students who are blind or have low vision. On the user's
> explicit action (taking a photo), it captures code displayed on a screen, whiteboard,
> projector, or printed page, then reads it verbatim and explains it through audio. Camera
> access is used solely to capture the content the user is actively looking at, only when
> the user initiates capture. Captured images are sent to MetaHelper's backend for AI
> processing (Google Gemini) to generate the spoken description and are not retained after
> processing. MetaHelper does not use the microphone or voice invocation.

**Disclose honestly:** images transit a third-party AI service (Google Gemini via the
backend) during processing.

## SDK version note

This repo pins `mwdat-core:0.3.0`; the latest documented is **0.7.0**. Consider upgrading
before submission — the docs and sample apps now target 0.7.x, and several preview bugs
(e.g. the numeric-`APPLICATION_ID` issue above) span versions.

## Not independently verified (confirm in-console)

- Exact Developer Center button/sidebar labels (portal is login-gated).
- Whether your country is in the supported list for the Developer Center.
- Whether the preview-era attestation/`NO_ELIGIBLE_DEVICE` issues still occur on 0.7.0
  (those reports are from 0.4.0–0.5.0; relevant only if you revive the SDK capture path).

## Sources

- https://wearables.developer.meta.com/ (Developer Center)
- https://wearables.developer.meta.com/docs/develop/dat/manage-projects/
- https://wearables.developer.meta.com/docs/develop/dat/build-integration-android/
- https://developers.meta.com/wearables/faq/ (access status, regions, publishing)
- https://developers.meta.com/blog/introducing-meta-wearables-device-access-toolkit/
- https://github.com/facebook/meta-wearables-dat-android (issues #65, #30, #80, #94, #115)
