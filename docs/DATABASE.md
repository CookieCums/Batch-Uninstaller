# UAD-ng database integration

## Source

- Project: https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- File: `resources/assets/uad_lists.json`
- Runtime endpoint: https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json
- License: GPL-3.0 for the upstream project; retain attribution and license references.
- Schema observed in upstream source: JSON object keyed by package ID. Each value can include `list`, `description`, `dependencies`, `neededBy`, `labels`, and `removal`.

## Parsing and matching

Package identifiers are exact, case-sensitive JSON object keys. Core metadata fields are parsed only when their types are valid. Optional arrays may be null or absent. Unknown fields are ignored for forward compatibility. A malformed root, invalid JSON, or a response without usable package entries is a failed update and must not overwrite the last valid cache.

## Display mapping

| Upstream `removal` | UI label |
| --- | --- |
| `Recommended` | Recommended |
| `Advanced` | Caution |
| `Expert` | High risk |
| `Unsafe` | High risk |
| Missing/unknown | Unknown |

The UI labels are a conservative display grouping. They are not a safety guarantee. The source's description, list, dependencies, neededBy, labels, and revision should be shown separately where available.

## Synchronization

Use HTTPS, bounded connection/read timeouts, conditional ETag/Last-Modified requests when supplied, and a validated last-known-good cache. Store source revision/ETag and successful retrieval time separately from failure state. A failed refresh must preserve cached content. Manual database refresh is distinct from installed-app rescan.

## Scheduling and offline behavior

Desired intervals are 1, 3, or 7 days, or disabled. Android background scheduling is inexact and subject to system constraints; WorkManager should be used for periodic updates. Offline use relies on a valid cache and must display its age. If no cache exists and fetching fails, show an explicit unavailable state.

## Attribution

Credit Universal Android Debloater Next Generation and link to the repository and database file. Include the applicable GPL-3.0 license information. The list is community-maintained; package recommendations may be incomplete, outdated, device-specific, or incorrect.
