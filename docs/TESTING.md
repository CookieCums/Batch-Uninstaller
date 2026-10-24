# Testing and verification

## Latest verified GitHub Actions run

- Push run: https://github.com/CookieCums/Batch-Uninstaller/actions/runs/40109610751
- Pull-request run: https://github.com/CookieCums/Batch-Uninstaller/actions/runs/40109614049
- Branch: `appsieve-redesign`
- Commit: `c107aa5e5331b7c5861475140022b5fc0e9b0bc0`
- Overall result: **success**
- Unit tests: **passed**, including parser/category-array behavior, conservative risk merging, cache-format migration, and SHA-256 backup integrity tests.
- Android lint: **passed**. Legacy/deprecated APIs still produce warnings; see the workflow artifact report.
- Debug APK: **assembled successfully**.
- Artifact: [appsieve-debug-and-reports](https://github.com/CookieCums/Batch-Uninstaller/actions/runs/40089402687/artifacts/12557499483)
- APK path in artifact ZIP: `outputs/apk/debug/app-debug.apk`
- Build type: debug build, signed by Android Gradle's automatically generated debug key. This verification predates the new uninstall-mode integration commit; download the newest successful workflow artifact before testing that feature.

## Checks performed

- Gradle wrapper and dependency resolution completed in GitHub Actions.
- Database parser tests cover category arrays, optional metadata, risk mapping, invalid identifiers, malformed/empty responses, restrictive duplicate merging, and old-cache migration.
- Restore tests cover stable SHA-256 values, modified backup detection, and missing files. Command builder tests cover both modes and shell-injection rejection.
- Android lint completed with no errors.
- `assembleDebug` completed successfully and produced a verifiable APK artifact.

## Manual checks for the new uninstall workflow

- On a test device with root, select a disposable user app in Restorable mode. Confirm a `restore.json` and verified base/split APK copies are created before the app is removed and the restore point row is stored.
- Fill or restrict backup storage and repeat; confirm that Restorable mode aborts before any uninstall command when preflight fails, and skips an app if its copy/hash verification fails.
- Select Permanent mode; verify the checkbox is mandatory and batches of 10+ also require the exact selected count. Verify the history preference records successful removals only.
- On a non-root device, verify system apps cannot enter the selection and user apps use the standard per-app Android uninstall confirmation.
- Check that system-app removal uses per-user package-manager commands; no feature should delete APKs from `/system` or `/product`.
- Restore Center, package installation on restore, restoring app data, and physical-device behavior are not yet validated or fully implemented.

## Runtime checks

No emulator/device installation or launch was performed in CI. The passing build proves compilation, unit tests, lint, and APK packaging; it does not validate device-specific root behavior, UI appearance on a physical device, or destructive package operations.

## Earlier failures and fixes

- CI initially could not resolve the obsolete `recyclerviewhelper` and `permissions` dependencies. Their remaining use was removed in favor of native AndroidX RecyclerView click handling.
- Kotlin compilation then failed because search/filter state fields were missing and the profile dialog still referenced the removed helper API. These declarations and profile adapter usage were repaired.
- Lint found ellipsized text using `maxLines="1"`, a stray `/>` in the profile-row layout, and a literal escaped newline in the manifest. All were corrected before the successful run linked above.
