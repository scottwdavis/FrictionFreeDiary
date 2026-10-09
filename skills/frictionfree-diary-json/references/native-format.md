# Native import contract

Checked 2026-10-08 against the working tree at `G:\My Drive\FrictionFreeDiary` (base commit `1b8179c54302410575a2bc12be80a2983ddaf633`, with uncommitted changes). Format version 1; app versionName 1.0.0.

## Root

| Field | Native type | Decoder default |
|---|---|---|
| version | Int | 1 |
| app | String | FrictionFreeDiary |
| exportedAt | Long | current milliseconds |
| entries | array of DiaryEntry | [] |
| notebooks | array of Notebook | [] |
| tags | array of Tag | [] |

Emit all six root fields. Native decoding is permissive about missing defaulted fields and ignores unknown keys. It does not enforce the app name or version, so our authoring profile checks both deliberately. Null is not interchangeable with omission for nonnullable fields. Do not output an array root or Day One/Facebook wrapper.

## DiaryEntry

| Field | Native type | Decoder default / authoring rule |
|---|---|---|
| id | String | No default; fresh entries use UUID v4 |
| title | String | "" |
| content | String | ""; preserve useful text |
| notebookId | String | default_personal; must resolve to a notebook |
| colorHex | String | #00000000 |
| createdAt | Long | current milliseconds; always emit the journal date |
| updatedAt | Long | current milliseconds; always emit, but import resets it |
| latitude | Double or null | null |
| longitude | Double or null | null |
| locationName | String or null | null |
| mediaUris | array of strings | [] |
| tags | array of strings | [] |
| isPinned | Boolean | false |
| isFavorite | Boolean | false |
| moodEmoji | String or null | null; single representative emoji by convention |
| isArchived | Boolean | false |

Only `id` is required by the Kotlin decoder; the guide describes some defaulted fields as required for authoring. UUID v4 is a good generation convention, not an enforced native constraint (the unit test uses `entry-1`). Use integer milliseconds, not seconds, strings, or floats. `Long` is signed 64-bit. The app changes `createdAt <= 0` to current time, so pre-epoch/epoch-zero chronology cannot be preserved through this import path without app changes. Positive historical timestamps with fewer than 13 digits can be correct; do not use digit count alone to reject them.

Supported entry colors: `#00000000` None, `#2E7D32` Emerald, `#1565C0` Ocean, `#E65100` Sunset, `#6A1B9A` Lavender, `#C2185B` Cherry, `#F57F17` Amber, `#00695C` Teal, `#37474F` Slate. Other entry colors fall back to neutral in the palette lookup. Notebook colors are hex strings used by the notebook UI.

## Notebook

| Field | Native type | Decoder default |
|---|---|---|
| id | String | required |
| name | String | required |
| description | String | "" |
| icon | String | book |
| colorHex | String | #2E7D32 |
| createdAt | Long | current milliseconds |
| isDefault | Boolean | false |

IDs are arbitrary nonblank unique strings; UUIDs are not required. Valid icon keys: `book`, `lightbulb`, `spa`, `favorite`, `star`, `work`, `school`, `flight`, `fitness_center`, `palette`, `home`, `code`, `music_note`, `pets`, `shopping_cart`. Unknown keys display the book icon.

App initialization creates these only when notebook count is zero:

| ID | Name | Description | Icon | Color | isDefault |
|---|---|---|---|---|---|
| default_personal | Personal | Everyday thoughts and reflections | book | #2E7D32 | true |
| default_ideas | Ideas | Inventions, inspirations, brainstorms | lightbulb | #1565C0 | false |
| default_reflections | Reflections | Deep gratitude and life lessons | spa | #6A1B9A | false |

Entries have a notebook foreign key. The native import routine inserts supplied notebooks first; it does not create a missing notebook based on an entry's ID. For new self-contained files include referenced notebooks, including a built-in definition if its existence is uncertain. This also handles a deleted Ideas/Reflections notebook. Existing notebook IDs are inserted with IGNORE: importing a different name/color for an existing ID will not rename/recolor it. Avoid defining unrelated notebooks.

## Tags and Markdown

Root Tag objects have `name: String` (required), `usageCount: Int = 0`, `lastUsedAt: Long = now`. Root tag metadata is decoded but unused by `importData`. Prefer `tags: []` at root. On save, tags are the distinct union of hashtags from title/content and explicit entry tags, lowercased/trimmed. Explicit tags do not have their leading `#` removed by the app; remove it before writing. Tag counts are recalculated, last-used time is import time, and the visible cloud excludes archived entries.

Hashtag extraction uses `(?<!\w)#([a-zA-Z0-9_\p{L}]+)`, skips purely numeric matches, and removes entire lines beginning (after left trim) with `# `, `## `, `### `, or `#### `. Thus a hashtag on a heading line is not indexed unless also in entry.tags. Hyphens terminate a hashtag. The implementation does not actually filter code fences or hex color strings despite a broader comment; avoid incidental `#words` if unwanted tags matter.

Renderer supports headings H1–H4, bold/italic, inline/fenced code, bullets, checklist display, consecutive blockquotes, rules, Markdown links including angle-bracket URLs, and standalone images. Checkboxes are rendered icons, not interactive task state. Numbered lists are plain text rather than a dedicated list block; Markdown tables/HTML are not general rich-layout features. Prefer paragraphs, headings, and bullets.

## Media and package formats

- **Portable ZIP package (`frictionfree-backup.zip`):**
  - Contains root `frictionfree-backup.json` and attached image files inside `media/`.
  - In package JSON, entries reference packaged files via relative paths (e.g. `media/image_1.jpg`).
  - During import, the app validates archive integrity, rejects directory traversal and unsafe paths, copies images to private `filesDir/media` with app-generated filenames, and rewrites entry `mediaUris` to the local paths. Newly copied files that are unreferenced (e.g. if an entry was skipped because its ID already exists) are cleaned up.
  - Portable packages must never contain remote URLs, absolute device paths, or PC paths in `mediaUris`.
- **Standalone plain JSON (`*.json`):**
  - Plain JSON import preserves existing behavior: `mediaUris` strings are stored as-is without copying or downloading files.
  - Use empty `mediaUris: []` for text-only conversions. Remote images embedded in Markdown syntax (`![alt](url)`) are not packaged or downloaded offline.

## Persistence and UI caveats

- Normal native UI import calls `importData(..., overwriteExisting = false)`: existing entry IDs are skipped, including changed content. Duplicates inside one input cause later occurrences to be skipped. Keep unique IDs and a persistent source map across regeneration.
- `saveEntry` sets `updatedAt` to now; positive `createdAt` survives. If original modification time matters, preserve it in content or the companion report.
- Imports proceed notebook-first, entry-by-entry with no whole-import transaction. A failure can leave a partial import. Reusing IDs makes retries skip entries already saved; generating new IDs can duplicate them.
- The success count reports input entries, including skipped IDs. It is not the count of newly inserted entries.
- Calendar/timeline use device-local time for display and day grouping. The file has no timezone field. Record important original timezone information in content or the report.
- Current Settings UI shows **Import Journal Data** with source choices **Day One Archive**, **Facebook Archive**, and **FrictionFree Backup**. Pick FrictionFree Backup for authored JSON. Older docs say Import from JSON. Explicit native selection bypasses heuristic Day One/Facebook JSON detection, which otherwise scans for quoted key strings.

## Source map for maintenance

Paths relative to the app repository:

- `app/src/main/java/com/frictionfree/diary/data/model/{ExportData,DiaryEntry,Notebook,Tag,EntryColor}.kt`: serialized contract and palette.
- `app/src/main/java/com/frictionfree/diary/utils/JsonExporter.kt`: JSON options, format routing, native import.
- `app/src/main/java/com/frictionfree/diary/data/repository/DiaryRepository.kt`: saveEntry, ensureDefaultNotebooks, importData.
- `app/src/main/java/com/frictionfree/diary/data/local/entities/DiaryEntryEntity.kt` and `data/local/dao/NotebookDao.kt`: foreign key and notebook conflict handling.
- `app/src/main/java/com/frictionfree/diary/utils/HashtagParser.kt`: tag extraction.
- `app/src/main/java/com/frictionfree/diary/ui/components/{NotebookIcons,MarkdownRenderer}.kt`: visible presentation support.
- `app/src/main/java/com/frictionfree/diary/ui/screens/settings/{SettingsScreen,SettingsViewModel}.kt`: source picker, success counts.
- `app/src/test/java/com/frictionfree/diary/JsonExporterTest.kt`: native serialization round trip.
- `AI_JSON_IMPORT_GUIDE.md`: useful examples, but older notebook/UI/UUID/Markdown claims need the qualifications above.
- `tools/facebook_chat_extractor/{journal_builder,prompts,deduplicator}.py`: precedent for authored event imports. Its date helper uses host-local naive datetimes, falls back to now on failure, and regenerates IDs; do not copy those behaviors into historical or repeatable conversions.
