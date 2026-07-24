# Local-First Reliability Contract

LeafFeed's core reading path must remain useful without a network connection or a successful
full-page parser request.

## Data

- Room data is the source of truth for subscriptions, articles, reading state, and account data.
- Reading collections use the versioned `leaffeed.collections` format. Version 2 includes a SHA-256
  integrity value; version 1 remains importable for existing backups.
- API keys are excluded from preference exports unless the user explicitly opts in.

## Synchronization

- Each account has one unique WorkManager chain for one-time synchronization and one periodic chain.
- A persisted summary records scope, attempt, progress, final state, and failed feed identifiers.
- A failed summary is actionable: the UI resolves identifiers to feed names when the local database
  still contains them and exposes retry access.
- A process death may leave a `RUNNING` summary temporarily; the next WorkManager attempt replaces
  it. No post-sync cleanup runs unless synchronization succeeds.

## Offline content

- Full-page content is stored under the app's persistent files directory, not the evictable Android
  cache directory.
- Existing readability cache files are migrated lazily on first access.
- Writes use a temporary file and atomic rename. A failed parser request falls back to the RSS
  description already stored in Room.
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
