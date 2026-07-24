# LeafFeed Performance and Stability Gates

This document defines the repeatable acceptance checks for changes that affect startup, article
lists, synchronization, reading, media, storage, or navigation. Measurements use a minified GitHub
release build on a physical device with animations at the system default.

## Test Data

Maintain three local-account fixtures:

- Small: 100 articles across 10 feeds.
- Medium: 10,000 articles across 100 feeds, including images and 100 podcast episodes.
- Large: 50,000 articles across 300 feeds, with at least 20 filter or highlight rules.

The fixture generator must use fixed timestamps and identifiers so database and rendering work is
comparable between revisions. Clear app data before cold-start measurements; keep the database for
warm-start and scrolling measurements.

## Measurements

Record at least 10 iterations for each scenario and report the median and 95th percentile:

1. Cold start to the first fully drawn feed screen.
2. Warm start to the first fully drawn feed screen.
3. Open an article list, then fling from the newest items through at least 200 rows.
4. Open an article with text and images, then return to the same list position.
5. Synchronize the medium and large fixtures while observing CPU time, peak memory, database time,
   and WorkManager completion state.
6. Apply title-only and title-plus-description rules to the large fixture.
7. Start, seek, pause, enqueue, download, and remove a podcast episode.

Use Macrobenchmark frame timing and startup metrics when the benchmark module is available. Until
then, capture Perfetto traces with the same scenario names and device configuration.

## Merge Gates

- No scenario may regress its checked-in main-branch median by more than 5 percent or its 95th
  percentile by more than 10 percent without an explicit explanation and updated baseline.
- Scrolling must not introduce new frozen frames. Frame overruns must not increase by more than
  5 percent relative to the baseline.
- Synchronization and rule processing must complete without ANRs, uncaught exceptions, duplicate
  WorkManager jobs, or an unbounded increase in memory.
- Returning from reading must preserve list position, filters, search text, and selected content
  type.
- Process death during synchronization, download, and settings editing must recover to a usable
  state without losing the account or corrupting the database.

## UI Matrix

For feeds, article list, reading, settings, podcast controls, and dialogs, capture screenshots for:

- dynamic light and dark color schemes;
- 1.0x and 2.0x font scales;
- left-to-right and right-to-left locales;
- a narrow phone and an expanded two-pane window;
- normal motion and system animations disabled.

Review touch targets, clipping, contrast, semantic labels, navigation direction, and unexpected
layout movement. Store the device, Android version, revision, build type, and dataset name with
every result.
