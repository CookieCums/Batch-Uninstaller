# AppSieve (formerly Batch Uninstaller)

AppSieve modernizes the legacy Batch Uninstaller Android app with contextual, community-maintained debloat guidance from [Universal Android Debloater Next Generation (UAD-ng)](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation).

> **Caution:** Debloating can break device features or cause instability. UAD-ng recommendations are community-maintained and may not match every device, manufacturer, ROM, or Android version. Review descriptions and warnings before taking action. No recommendation is executed automatically.

## Current features

- Installed application inventory with icons, package IDs, install dates and APK sizes.
- Existing sorting, system/user app filters, saved profiles, batch uninstall workflow and root-aware behavior.
- Select an app row to reveal its UAD-ng recommendation card directly below the row.
- Displays the upstream removal classification, list category, description, labels, dependencies and packages that depend on it where provided.
- Exact package-ID matching; packages absent from the database are explicitly marked as having no match.
- Fetches the maintained UAD-ng JSON at runtime over HTTPS.
- Validated local cache, ETag/Last-Modified conditional requests, bounded download size/timeouts, offline cache use, and background refresh scheduling.
- Separate **Update database now** and **Scan installed apps now** actions.
- Configurable automatic database refresh: daily, every 3 days, every 7 days, or disabled.
- Original AppSieve adaptive launcher mark and updated teal design palette.

## Android compatibility

- Minimum SDK: 16 (Android 4.1), retained from the legacy app.
- Target/compile SDK: 33 in the initial modernization branch.
- Application ID remains `com.saha.batchuninstaller` to avoid creating a separate Android app identity.
- Standard uninstall requests still rely on Android confirmation. Silent system-package management requires appropriate privilege and is not granted by the UAD-ng database.

## Build

See [docs/BUILDING.md](docs/BUILDING.md). The CI workflow runs unit tests, Android lint, and debug APK assembly on GitHub Actions and uploads the debug APK and reports as artifacts.

## Documentation

- [Research](docs/RESEARCH.md)
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Database integration and attribution](docs/DATABASE.md)
- [Building](docs/BUILDING.md)
- [Signing](docs/SIGNING.md)
- [Testing and verification](docs/TESTING.md)

## Upstream attribution and licensing

The original Batch Uninstaller project is GPL-3.0; see [LICENSE](LICENSE). UAD-ng is also GPL-3.0 and its community database is maintained in the [UAD-ng repository](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation). The app fetches the database from the upstream `resources/assets/uad_lists.json` file at runtime rather than treating a bundled snapshot as authoritative.

## Known limitations

- The first modernization increment keeps the legacy Android Views architecture; a full Compose/Material 3 migration is not yet complete.
- Root execution remains device-specific and should be reviewed separately before relying on it for critical packages.
- CI build status and runtime behavior must be verified from actual workflow results; passing compilation alone does not validate every device-specific operation.
