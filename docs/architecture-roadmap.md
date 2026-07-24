# Stability and Architecture Roadmap

This roadmap records the accepted recommendations for synchronization, diagnostics, testing, and
feature growth. It is intentionally incremental: behavior and test boundaries are stabilized before
moving code into separate Gradle modules.

## Current contracts

- WorkManager sync is unique per account. A manual request does not replace another account's work.
- Post-sync reader and widget work runs only after a successful sync.
- Sync exceptions are written to the existing bounded sync log and exposed to the UI as a classified
  failure where possible.
- Downloads without `Content-Length` are valid and report indeterminate progress rather than a bad
  percentage or a false size mismatch.
- Rule diagnostics are pure and side-effect free, so a preview can explain applicable and matching
  rules without modifying an article.
- Persisted sync summaries include failed local-feed identifiers and are exposed through a dedicated
  status screen with live progress and retry access.
- Parsed article content, AI summaries, and podcast downloads have separate cache controls.
- Account-wide podcast, tag/note/search, and article-rule management views are reachable from their
  corresponding settings sections.

## Next implementation slices

1. Add WorkManager and Room migration integration fixtures for process death and retry recovery.
2. Add MockWebServer contracts for RSS, Fever, Google Reader-compatible, FreshRSS, and AI streams.
3. Move pure rule, RSS protocol, and failure classification code into `core` modules after the
   integration tests are stable.
4. Resolve failed-feed identifiers to user-facing feed names while preserving diagnostic redaction.

Performance measurements should use the fixed datasets and regression limits in `performance.md`.
