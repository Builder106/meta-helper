# Developer environment

## Audit prerequisites

Run `node scripts/doctor.mjs` from the repository root to audit local prerequisites.
Node.js 24 or newer is the tooling baseline. Use `--profile shell`,
`--profile backend`, or `--profile web` to narrow the audit; `--json` produces
a machine-readable report. The audit is offline and does not install dependencies.
Exit 1 means a failed prerequisite; exit 2 means invalid command arguments.

## Select the backend JDK

The backend requires Java JDK 26 with both `java` and `javac` available.
Set `JAVA_HOME` to an absolute JDK 26 directory, then run
`node scripts/doctor.mjs --profile backend`. JAVA_HOME takes precedence over PATH.
Android, shared, and iOS use Java 21-compatible toolchains separately.
Do not change their toolchains when selecting backend Java.

## Repair shell PATH

PATH entries must be absolute and nonempty. Remove relative directories and
empty entries, and make Git available on PATH. SHELL should point to an absolute
executable login shell; an unset SHELL is only a warning in noninteractive jobs.
Re-run `node scripts/doctor.mjs --profile shell` after fixing the environment.

## Restore ffmpeg

Backend audio processing requires ffmpeg. Install ffmpeg on the build host and
make its executable available on PATH, then run
`node scripts/doctor.mjs --profile backend`. The backend container already
includes ffmpeg. See `backend/Dockerfile` for the container build definition.
