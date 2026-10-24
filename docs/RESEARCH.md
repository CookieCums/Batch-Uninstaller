# Research findings

## Repository baseline

- Repository: https://github.com/CookieCums/Batch-Uninstaller
- Default branch at research time: `master`
- Baseline commit: `bfa133698a36b895eb66b175ccd92600ecc94059` (21 November 2022)
- Upstream project: https://github.com/sarbajitsaha/Batch-Uninstaller
- Existing application ID/namespace: `com.saha.batchuninstaller`
- Existing stack: Kotlin, Android Views/View Binding, RecyclerView, Room, Android Gradle Plugin 7.3.1, Kotlin 1.7.20, Gradle 7.4, Java 8 source compatibility.
- Existing SDK configuration: compile SDK 33, min SDK 16, target SDK 33.
- Existing functions: installed-app inventory; app icon/name/size/first-install date; sorting and system/user filtering; batch uninstall via Android confirmation flows or root; saved profiles; day/night mode.
- Existing risks: deprecated `AsyncTask`/ProgressDialog patterns, main-thread Room queries, legacy dependencies and JCenter configuration, shell command construction in root package operations, no current CI workflow, and no debloat-list integration.
- Preserve the current application ID during this modernization to avoid an unnecessary package identity migration.

## UAD-ng source and schema

Verified upstream project: https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation

The current source tree contains `resources/assets/uad_lists.json`. Runtime URL:
`https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json`

The file is a JSON object keyed by Android package identifier. A representative entry has `list`, `description`, `dependencies`, `neededBy`, `labels`, and `removal`. The upstream Rust model uses `list`, `description`, and `removal` as core fields; related lists/labels may be empty or null. Current observed removal values include `Recommended`, `Advanced`, `Expert`, and `Unsafe`. Observed list values include `Oem`, `Aosp`, `Google`, `Misc`, `Pending`, and `Carrier`.

Use only exact source values for classification. Conservative UI mapping: Recommended -> Recommended; Advanced -> Caution; Expert and Unsafe -> High risk; missing/unrecognized values -> Unknown. These are UI groupings, not a claim that upstream's labels guarantee safety. Never infer safety from the existence of an entry.

The UAD-ng repository is GPL-3.0. Preserve attribution, license notices, a link to the upstream source, and the fact that database content is community-maintained. Review all redistribution obligations before publishing derivative binaries.

## UX/reference research

- UAD-ng provides package search/filtering, removal levels, list/source categories, package descriptions, and refresh of its cached JSON list. It is a desktop Rust/ADB application, not an Android-native UI.
- Its usage documentation explicitly warns that system apps are not physically removed from the system partition without root.
- Canta is referenced by UAD-ng as an Android-native project that integrates UAD data and Shizuku. This supports treating privileged execution as a separate, explicit capability rather than pretending ordinary Android APIs can silently remove arbitrary system packages.
- Android package visibility is sensitive; this app declares QUERY_ALL_PACKAGES. Keep this permission tied to the app's core app-management use case and explain it in privacy documentation.

## Candidate identity

Candidates considered: **AppSieve**, **Debloat Desk**, and **Package Atlas**. Use **AppSieve** as the working product name: short, distinct from the legacy title, and descriptive of inspecting/filtering installed packages. This is a preliminary repository/name search, not legal trademark clearance. Keep application ID `com.saha.batchuninstaller` stable for upgrade compatibility; a future ID migration would create a separate app identity unless carefully handled.

## Compatibility decisions

- Keep the existing Kotlin/View-based app initially to avoid a risky all-at-once rewrite of the existing uninstall and profile flows.
- Add the upstream data integration as a separate, testable repository before replacing core navigation.
- Keep minimum SDK 16 for now because it is the declared legacy baseline; verify dependency/toolchain constraints before any SDK increase.
- Build with the existing Gradle 7.4 / AGP 7.3.1 toolchain and JDK 11 first; upgrade the toolchain only after baseline CI is established.
- Database fetches must use HTTPS, bounded timeouts and payload size, validate the full JSON before cache replacement, and retain a last-known-good cache after failures.
