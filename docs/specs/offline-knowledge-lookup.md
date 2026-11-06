# Offline knowledge lookup

Scope: a one-shot Node.js 24+ command alongside the existing developer tooling.
No frontend, application API changes, daemon, network lookup, or extra packages.
Curated repository runbooks live in `docs/runbooks/*.md`; each uses one H1 title
and H2 sections. Update the runbooks when their source procedures change.

Acceptance criteria:

- `node scripts/knowledge.mjs <query...>` finds sections containing all query
  terms, ignoring case and normalizing Unicode compatibility characters.
  Punctuation separates terms; matching uses whole tokens, without stemming,
  substring matching, or remote fallback.
- An in-memory inverted index is built once per invocation and reused by the
  exported search function. Queries intersect postings starting with the
  rarest term. No persisted index can become stale between invocations.
- Results rank by term frequency, with title terms weighted four times body
  terms; ties sort by file path then section line. Each result includes its
  repository-relative path, one-based heading line, title, and bounded excerpt.
- The default returns five results; `--limit 1..20` controls the maximum and
  `--json` returns `{ query, results }`, including numeric relevance scores.
  No matches is a successful empty result. Invalid arguments exit 2; unreadable,
  empty, or oversized corpora exit 1 with an actionable message.
- Lookup works from any working directory, reads only local curated Markdown,
  skips symlinks, and writes no files. Input is limited to 256 query characters,
  16 distinct terms, 128 files, 256 KiB per file, and 4 MiB total corpus size.
- Tooling tests cover retrieval, ranking, references, bounds, and the real CLI.
  The existing verification workflow discovers the command and tests in CI.
