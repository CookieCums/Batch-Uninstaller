# Testing and verification

## Latest verified GitHub Actions run

- Run: https://github.com/CookieCums/Batch-Uninstaller/actions/runs/40089402687
- Branch: `modernization/uad-ng-ui`
- Commit: `fc4a5189b2b7e0cdee686b5bc3e3f4ab4b82a340`
- Overall result: **success**
- Unit tests: **passed**. The UAD parser test suite reports 5 tests, 0 failures, 0 errors; the existing example unit test also passed.
- Android lint: **passed** with 0 errors and 55 warnings. Remaining warnings include legacy/deprecated APIs and dependencies that are not build-blocking; see the uploaded lint report.
- Debug APK: **assembled successfully**.
- Artifact: [appsieve-debug-and-reports](https://github.com/CookieCums/Batch-Uninstaller/actions/runs/40089402687/artifacts/12557499483)
- APK path in artifact ZIP: `outputs/apk/debug/app-debug.apk`
- APK SHA-256: `2d03027113f04ebfedb232d9d99a19083139c35d76e113e759c68e24c324e9d1`
- Build type: debug build, signed by Android Gradle's automatically generated debug key.

## Checks performed

- Gradle wrapper and dependency resolution completed in GitHub Actions.
- UAD database parser unit tests cover representative metadata, null optional arrays, risk mapping, invalid package identifiers, and malformed/empty responses.
- Android lint completed with no errors.
- `assembleDebug` completed successfully and produced a verifiable APK artifact.

## Runtime checks

No emulator/device installation or launch was performed in CI. The passing build proves compilation, unit tests, lint, and APK packaging; it does not validate device-specific root behavior, UI appearance on a physical device, or destructive package operations.

## Earlier failures and fixes

- CI initially could not resolve the obsolete `recyclerviewhelper` and `permissions` dependencies. Their remaining use was removed in favor of native AndroidX RecyclerView click handling.
- Kotlin compilation then failed because search/filter state fields were missing and the profile dialog still referenced the removed helper API. These declarations and profile adapter usage were repaired.
- Lint found ellipsized text using `maxLines="1"`, a stray `/>` in the profile-row layout, and a literal escaped newline in the manifest. All were corrected before the successful run linked above.
