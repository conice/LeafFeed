# Local-First Reliability Contract

LeafFeed's core reading path must remain useful without a network connection or a successful
full-page parser request.

## Data

- Room data is the source of truth for subscriptions, articles, reading state, and account data.
- Account credentials use versioned AES-GCM encryption backed by an Android Keystore key. Legacy DES
  rows are readable only for in-place migration and are never used for new writes.
- Android system backup and device transfer are excluded because the credential key is device-local;
  users move subscriptions and settings through LeafFeed's explicit exports and re-enter credentials.
- Reading collections use the versioned `leaffeed.collections` format. Version 2 includes a SHA-256
  integrity value; version 1 remains importable for existing backups.
- API keys are excluded from preference exports unless the user explicitly opts in.

## Synchronization

- Each account has one unique WorkManager chain for one-time synchronization and one periodic chain.
- A persisted summary records scope, attempt, progress, final state, and failed feed identifiers.
- Running progress is rate-limited before it is persisted so large subscriptions do not turn a
  synchronization into hundreds of DataStore and WorkManager database writes. Completion is
  always published immediately.
- A failed summary is actionable: the UI resolves identifiers to feed names when the local database
  still contains them and exposes retry access.
- A process death may leave a `RUNNING` summary temporarily; the next WorkManager attempt replaces
  it. No post-sync cleanup runs unless synchronization succeeds.

## Reader content safety

- Feed HTML is untrusted input. The WebView renderer removes scripts, embedded browsing contexts,
  event handlers, form controls, and active URL schemes before loading an article.
- A restrictive content security policy blocks frames, objects, form submissions, external styles,
  and fetch/XHR/WebSocket connections. Reader-provided JavaScript is limited to formatting and the
  optional image-click bridge.
- Relative resources use only a validated HTTP(S) article URL. Proxied image requests send an
  origin-only referrer, never the article path, query, credentials, or body content.

## Offline content

- Full-page content is stored under the app's persistent files directory, not the evictable Android
  cache directory.
- Existing readability cache files are migrated lazily on first access.
- Writes use a temporary file and atomic rename. A failed parser request falls back to the RSS
  description already stored in Room.
- Background full-page prefetch uses bounded batches, honors the account's network policy and a
  battery-not-low constraint, and treats individual pages as best-effort. A permanently broken
  page must not cause the whole article set to retry indefinitely.
- Clearing full-page content does not remove Room articles, reading state, tags, notes, or backups.

## Performance fixtures

The fixed test sizes are 100 articles/10 feeds, 10,000 articles/100 feeds, and 50,000 articles/300
feeds. Performance changes should report the dataset, build type, device, median, and p95 before
changing a checked-in baseline.

## Service differences

`SyncServiceCapabilities` is the single source of truth for what a remote service persists. Local
collections such as tags, notes, and saved searches are never presented as remotely synchronized
unless the service contract explicitly supports them.

Feedly and Inoreader account identifiers remain readable for migration compatibility, but the
current runtime has no remote implementation for them and fails explicitly instead of silently
using the local RSS service.
