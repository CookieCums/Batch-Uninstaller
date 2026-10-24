# Architecture

## Current baseline

The app is a Kotlin Android Views application with a single large `MainActivity`, a RecyclerView adapter, package utilities, root utilities, and Room-backed profile storage. This structure is preserved during the first database/CI increment to reduce risk to the established uninstall flow.

## Target separation

- **UI:** Activity, RecyclerView rows, package detail/card UI, selection and confirmation screens.
- **Installed package inventory:** PackageManager-backed scan; reports only packages the OS exposes to this app.
- **Debloat repository:** Fetches and validates the upstream JSON; returns typed package records and source revision.
- **Cache:** Last-known-good JSON plus ETag/Last-Modified metadata and successful update timestamp.
- **Risk mapping:** Pure function translating the upstream `removal` field into conservative display categories.
- **Settings:** Preferences for theme and database update interval.
- **Operations:** Existing standard uninstall intents and root-based behavior remain separate from community recommendations. All destructive actions require user confirmation.
- **CI/tests:** Unit tests validate parsing, matching, and risk mapping; Actions builds and archives a debug APK.

## Data flow

1. The app starts without requiring root.
2. It reads a validated cached database if available.
3. A background refresh requests the HTTPS upstream JSON with conditional headers when possible.
4. The complete response is parsed and validated before replacing the cache.
5. Installed package identifiers are matched exactly against the database keys.
6. UI presents upstream notes and labels with revision/age metadata; missing matches are shown as unknown.
7. Package inventory rescan and database refresh are separate actions.

## Execution limitations

Standard Android uninstall intents can ask the user to approve ordinary uninstall requests. They do not grant silent removal of arbitrary system packages. Root operations are privileged and device-specific; they must not be implied by database classification.
