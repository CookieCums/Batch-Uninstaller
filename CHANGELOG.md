# Changelog

## Unreleased

- Fix Android CI compilation by removing unavailable RecyclerView/permission dependencies and replacing legacy helper usages with AndroidX click handling.
- Fix lint failures in the app list, profile row layout, and Android manifest.
- Verify unit tests, Android lint, and debug APK assembly in GitHub Actions; document the successful artifact and its SHA-256.

- Begin modernization research and document the verified legacy baseline.
- Document UAD-ng database schema, conservative risk mapping, attribution, and cache/update expectations.
- Add a reproducible GitHub Actions build/test/lint pipeline.
