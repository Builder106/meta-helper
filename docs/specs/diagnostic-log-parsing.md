# Diagnostic log parsing

Scope: a one-shot Node.js 24+ command alongside the existing developer tooling.
No frontend, application API changes, daemon, network requests, or extra packages.

Acceptance criteria:

- `node scripts/logs.mjs [options] [files...]` classifies log output using automated
  regular expressions against known failure classes: process crashes and out-of-memory
  panics, upstream service unavailability (502/503/504), rate limit exhaustion (429),
  network timeouts and connection resets, authentication and permission denials (401/403),
  uncaught runtime exceptions, subprocess pipeline failures (ffmpeg/spawn), build/test
  failures, resource exhaustion (disk/file descriptors), and deprecation warnings.
- Supports file inputs and piped standard input (`-`). Multi-line stack traces and
  continuous error frames (`\tat ...`, `Caused by:`, `... more`) are grouped with
  their root cause event.
- Repeated anomalies are grouped into deduplicated clusters by signature, reporting
  severity, category, occurrence count, first/last line numbers, bounded sample excerpt,
  and an actionable diagnostic remedy.
- CLI flags support automation and filtering:
  - `--json`: machine-readable report with summary metrics and anomaly clusters.
  - `--severity <critical|error|warn|all>`: filter anomalies by minimum severity.
  - `--category <name>`: filter anomalies by category name.
  - `--limit 1..100`: maximum anomaly groups to display (default: 20).
  - `--fail-on <critical|error|warn>`: exit code 1 if any anomaly meets or exceeds the threshold.
  - `--pattern <regex>`: custom regular expression anomaly rule.
- Bounded processing: limits input files to 10 MiB each and 100,000 total lines,
  refusing symlinks, directory targets, or unreadable files safely.
- Exit codes: 0 for successful execution (no threshold violations), 1 for unreadable
  inputs or fail-on threshold triggered, 2 for invalid arguments or invalid regex.
- Tooling tests cover classification rules, multi-line traces, grouping, CLI flags,
  stdin/file I/O, bounds, and exit codes. Existing workflow automation discovers
  the script and tests automatically.
