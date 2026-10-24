# Implementation plan

## Stages

1. **Baseline and evidence** — inspect source, SDK/toolchain, permissions, root operations, upstream database schema/license, and history. Record verified findings in `docs/RESEARCH.md`.
2. **Build foundation** — establish reproducible GitHub Actions for tests, lint, and debug APK artifacts before broad refactoring.
3. **Database layer** — add a typed UAD-ng parser, conservative risk mapping, HTTPS refresh, conditional requests, validated cache replacement, and parser tests.
4. **App-list integration** — show source-backed recommendation details alongside installed packages, distinguish no-match/unknown states, and keep database refresh independent from package rescans.
5. **UX and settings** — modernize the list/detail/selection flow, add theme and update preferences, status/error/empty states, and accessibility improvements.
6. **Operations hardening** — review shell argument safety and root/non-root behavior, add explicit review/confirmation, report real results, and avoid destructive automatic actions.
7. **Verification** — inspect CI logs and artifacts, fix failures, run tests, and document what was or was not verified on a real device/emulator.

## Guardrails

- Work on `modernization/uad-ng-ui`; do not force-push or overwrite unrelated history.
- Preserve `com.saha.batchuninstaller` while this remains an upgrade of the existing app.
- Never execute a database recommendation automatically.
- Treat absent/unknown metadata conservatively.
- Do not claim runtime/device validation unless it was actually performed.
- Keep signing material out of the repository; CI initially produces debug-signed development APKs.
