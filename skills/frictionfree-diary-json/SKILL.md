---
name: frictionfree-diary-json
description: Create and validate native FrictionFree Diary (FlowDiary) import JSON from journal text, notes, documents, transcripts, or authorized email material. Use for this app's entries, notebooks, and tags, including preparing Gmail-derived journal imports when requested.
---

# FrictionFree Diary JSON

Produce a UTF-8 `.json` file or portable `.zip` backup package the Android app can import through **Settings → Import Journal Data → FrictionFree Backup**. This skill prepares files; retrieving Gmail or importing into the running app is a separate action within the user's requested scope.

Read [references/native-format.md](references/native-format.md) before authoring. It records the actual Kotlin model and persistence behavior, including differences from the repository's older AI guide. [assets/example-import.json](assets/example-import.json) is a complete synthetic example; replace its content, IDs, and dates when creating new entries.

## Package layout and media

- **Text-only imports:** Continue to author and validate standalone UTF-8 `.json` files with `mediaUris: []`.
- **Entries with attached images:** Package as a ZIP archive containing:
  - `frictionfree-backup.json`: the native JSON backup file.
  - `media/`: folder containing actual image files (e.g. `media/image_1.jpg`, `media/photo_2.png`).
- In portable ZIP packages, `mediaUris` must contain package-relative paths starting with `media/` (e.g. `["media/sunset.jpg"]`). Do not use local absolute PC paths, web URLs, or email attachment IDs in `mediaUris`.
- Do not silently download remote images or claim images are included when only descriptions or URLs exist; only bundle supplied image files. Absolute device file paths (e.g. `/data/user/0/...`) belong solely to on-device exports.

## Authoring

- Preserve the requested scope and writing style. For direct conversions, preserve the original text and meaningful structure. For extracted memories, retain concrete people, places, actions, and source-supported details; do not present plans as completed events or invent feelings, quotes, attendance, or outcomes. The existing Facebook extractor's third-person style and event-only filtering are workflow choices, not requirements of the native JSON format.
- Choose entry boundaries appropriate to the request: one note, message, thread, or event. Merge overlapping material only when it clearly describes the same event. Keep planned activities labeled as plans unless the source confirms they happened.
- Set `createdAt` to the intended journal/event date, not the generation time. Resolve relative dates against the source date and timezone. Calculate integer epoch milliseconds with an explicit offset or timezone. If only a calendar date is known, use a disclosed date-only convention (noon in the agreed timezone is useful), and mark the time as approximate in content or the companion report. Ask when missing dates or timezone would materially change chronology; never silently replace an invalid historical date with now.
- Generate fresh entry IDs with `uuid.uuid4()`. For repeatable conversions, save a companion source-key-to-UUID-v4 map and reuse IDs for the same records on reruns. Do not put tracking fields into native objects: unknown fields are silently discarded. Existing backup IDs can be preserved even if they are not UUIDs.
- Include every notebook referenced by the entries for a self-contained file, using matching IDs and `isDefault: false` for custom notebooks. If the user identifies an existing notebook, reuse its exact ID and validate with `--existing-notebook ID`; a display name alone is insufficient. Default notebooks can be deleted in this app, so do not assume all built-in IDs still exist.
- Use plain Markdown for content, normalized lowercase tags without `#`, and palette colors. Default to neutral color and false flags unless the request or source gives a reason to change them. Leave unknown coordinates and mood null. URLs belong in Markdown links.
- Serialize with a JSON library, for example Python `json.dump(data, file, ensure_ascii=False, indent=2, allow_nan=False)`. Include `version: 1`, `app: "FrictionFreeDiary"`, generation-time `exportedAt`, and the three arrays. Keep root `tags: []`; put usable tags on each entry. Supply both timestamps explicitly, normally `updatedAt = createdAt` if no modification date is known.

## Validate and deliver

Run the included validator on the actual output (both `.json` and `.zip` packages are supported):

```powershell
python "<skill-folder>/scripts/validate_import.py" "<output-file.json or output-file.zip>"
```

Its default profile checks newly authored, self-contained files or packages. For an existing native export with omitted defaults or non-UUID IDs, use `--native-export`. For ZIP packages, it verifies that `frictionfree-backup.json` is present, image entries under `media/` are valid and recognized images, all referenced `mediaUris` exist without unsafe paths, and uncompressed size / asset count limits are respected. Reopen the file to check entry count, notebook assignments, earliest/latest dates, Unicode, Markdown, and source coverage. For a substantial batch, retain a concise companion report with source-to-entry mapping, reused IDs, unresolved dates, exclusions, and timezone assumptions; keep that report outside the JSON.

Deliver a file link, entry/notebook counts, and any material import limitation. Explain that reimporting the same IDs skips those entries, including edits; new IDs create additional entries. The app replaces `updatedAt` with import time and copies bundled ZIP photos to private app storage, removing any unreferenced or skipped assets. Do not call a code-level validation a successful device import.

## Later Gmail conversions

When Gmail work is requested, use the available authorized source access and the user's filters. This skill does not itself provide a Gmail connector. Preserve sender/recipient, subject, relevant message date, and source identifiers or links in content or a companion report where useful. Keep message time separate from a mentioned event time. Remove repeated quoted chains only when no unique information is lost. Do not turn all emails into asserted life events or impose the Facebook extractor's narrative policy. Preserve a source map across batches so repeat processing does not create duplicate journal entries.

## Refreshing compatibility

The reference was checked against the working tree at `G:\My Drive\FrictionFreeDiary` on 2026-10-08, which contains uncommitted importer changes. When the app version changes or an import fails, reread the model classes, `JsonExporter.kt`, `DiaryRepository.kt`, notebook DAO/entity, and `HashtagParser.kt` listed in the reference. Prefer running code over an older guide; update this skill and validator together when the contract changes.
