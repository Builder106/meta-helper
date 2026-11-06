# Backend verification

## Run the verification workflow

Run `node scripts/workflow.mjs verify` from the repository root for tooling and
backend checks, or `node scripts/workflow.mjs verify-backend` for backend only.
Prerequisites are Node.js 24+, Java JDK 26, and ffmpeg. The workflow runs Gradle
clean, tests, JaCoCo report, the coverage gate, and bootJar with warnings treated
as errors and no persistent daemon. Gradle may download uncached dependencies.
Use `--dry-run` to inspect commands without executing them.

## Investigate test or coverage failures

Read `backend/build/reports/tests/test/index.html` for failing tests and
`backend/build/reports/jacoco/test/html/index.html` for JaCoCo coverage details.
The backend service and controller packages require 100 percent line coverage.
Fix failures and exercise uncovered behavior before repeating verification.
Do not lower the coverage threshold or disable compiler warnings to pass checks.

## Restore Gradle wrapper files

The backend uses its own `backend/gradlew`, `backend/gradle/wrapper/gradle-wrapper.jar`,
and `backend/gradle/wrapper/gradle-wrapper.properties`. Restore missing tracked
wrapper files from Git and ensure gradlew is executable. Run
`node scripts/doctor.mjs --profile backend` before retrying verification.
Do not replace the wrapper with an arbitrary system Gradle version.

## Diagnose a workflow deadline

The workflow stops at the first failure. Tooling commands have a 60-second
deadline and the backend command has a 15-minute deadline. Exit 1 covers a
failed command, unavailable command, or timeout; exit 2 means invalid arguments.
Inspect the named failing step and its output. Check prerequisites and access
to configured Gradle dependency repositories before retrying a timed-out build.
