# Debloat database integration

## Source and live verification

The source of truth is [MuntashirAkon/android-debloat-list](https://github.com/MuntashirAkon/android-debloat-list), branch `master`, distributed under AGPL-3.0. Each category is a separate JSON array hosted at a raw HTTPS URL:

- [aosp.json](https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master/aosp.json)
- [carrier.json](https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master/carrier.json)
- [google.json](https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master/google.json)
- [misc.json](https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master/misc.json)
- [oem.json](https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master/oem.json)

Verified for this change: all five raw endpoints return JSON arrays. The observed counts at verification were 255 AOSP entries, 207 Carrier, 177 Google, 404 Misc and 3,758 OEM entries. Counts can change as upstream is updated.

## Schema and parser behavior

Each array entry is keyed by its `id` package identifier. Supported fields include `label`, `description`, `dependencies`, `required_by`, `tags`, `web`, `removal`, `warning` and `suggestions`. Missing or malformed optional arrays are treated as empty. Entries without a valid `id` or `removal` are discarded and counted. Unknown removal values are normalized to `caution`. Duplicate package IDs across categories keep the most restrictive classification (unsafe > caution > replace > delete).

The parser retains support for the former AppSieve single-object UAD-ng cache as a migration fallback. Installed apps are matched by exact, case-sensitive package ID.

## Refresh and cache safety

- Downloads run off the main thread, with at most three files in flight.
- Every request has a 15-second connect timeout, a 30-second read timeout, an 8 MiB per-file limit and an identifying User-Agent.
- ETag conditional requests are sent per category when a valid cached file and ETag exist. HTTP 304 reuses the validated local file.
- Transient I/O and server errors retry up to three times with 1, 2 and 4 second backoffs. HTTP 404 and malformed/empty JSON fail immediately and identify the failing file.
- All five files must be present and valid before they are written to a staging directory and swapped into place. A failed update leaves the previous category directory intact.
- ETags and the last successful check time are updated only after cache installation.
- Upstream snapshots are bundled under `app/src/main/assets/debloat/` to seed first-run offline use. Online refreshes remain authoritative.

## Scheduling and UI behavior

Automatic WorkManager refreshes are opt-in and use an unmetered network by default. The manual **Update database now** action reports the entry count on success and includes concrete file/HTTP/network details on failure. Refreshing the database remains separate from rescanning installed apps.

## Attribution and caution

The Android Debloat List is maintained by MuntashirAkon and contributors under AGPL-3.0. Preserve the upstream attribution and license references. AppSieve itself remains based on GPL-3.0 Batch Uninstaller by Sarbajit Saha. This list is community-maintained guidance, not a guarantee; users should review descriptions and warnings before removal.
