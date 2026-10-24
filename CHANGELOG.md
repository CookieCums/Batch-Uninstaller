# Changelog

## Unreleased

- Fix Android CI compilation by removing unavailable RecyclerView/permission dependencies and replacing legacy helper usages with AndroidX click handling.
- Fix lint failures in the app list, profile row layout, and Android manifest.
- Verify unit tests, Android lint, and debug APK assembly in GitHub Actions on `appsieve-redesign`.
- Add Material 3 day/night fallback colors, Android 12+ dynamic colors, rounded app cards, and separate app/package/date/size rows.
- Replace the single-file debloat updater with five verified MuntashirAkon category arrays, ETag-aware refresh, retries, staged cache swap, bundled offline data, parser tests, and AGPL attribution.
- Add Room restore-point bookkeeping, a verified APK/split backup repository, a Restorable/Permanent confirmation selector, safe root command construction, serialized batch uninstall handling, and unit tests for command injection/integrity.

- Begin modernization research and document the verified legacy baseline.
- Document UAD-ng database schema, conservative risk mapping, attribution, and cache/update expectations.
- Add a reproducible GitHub Actions build/test/lint pipeline.
