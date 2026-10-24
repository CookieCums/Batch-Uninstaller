# Architecture

## Current baseline

The app is a Kotlin Android Views application with a single large `MainActivity`, a RecyclerView adapter, package utilities, root utilities, and Room-backed profile storage. This structure is preserved during the first database/CI increment to reduce risk to the established uninstall flow.

## Target separation

- **UI:** Activity, RecyclerView rows, package detail/card UI, selection and confirmation screens.
- **Installed package inventory:** PackageManager-backed scan; reports only packages the OS exposes to this app.
- **Debloat repository:** Fetches and validates five category arrays from MuntashirAkon/android-debloat-list; returns typed package records and per-file revision metadata.
- **Cache:** Bundled offline category snapshots plus a staged last-known-good category directory and per-file ETags/successful-check timestamp.
- **Restore/uninstall:** A Room `RestorePoint` table (migration 1→2), per-app APK/split copy metadata, `restore.json`, SHA-256 verification, a Restorable/Permanent confirmation selector, root command interface, and serial uninstall flow. Restorable mode preflights space and skips apps whose backup cannot be verified. The Restore Center UI, APK installation workflow, and optional data archives are still outstanding.
- **Risk mapping:** Pure function translating the upstream `removal` field into conservative display categories.
- **Settings:** Preferences for theme and database update interval.
- **Operations:** Root commands are behind `RootExecutor`; validated package identifiers are required before shell commands are built. Restorable app removal is permitted only after verified APK backups; permanent mode requires an explicit checkbox and, for batches of 10+, typing the batch count. System APK files are never deleted from `/system` or `/product`.
- **CI/tests:** Unit tests validate parsing, matching, and risk mapping; Actions builds and archives a debug APK.

## Data flow

1. The app starts without requiring root.
2. It reads a validated cached database if available.
3. A background refresh downloads up to three raw category JSON files concurrently with per-file conditional headers.
4. All five categories are validated before the staging directory replaces the cache; invalid responses leave the previous directory intact.
5. Installed package identifiers are matched exactly against the database keys.
6. UI presents upstream notes and labels with revision/age metadata; missing matches are shown as unknown.
7. Package inventory rescan and database refresh are separate actions.

## Execution limitations

Standard Android uninstall intents can ask the user to approve ordinary uninstall requests. They do not grant silent removal of arbitrary system packages. Root operations are privileged and device-specific; they must not be implied by database classification.
