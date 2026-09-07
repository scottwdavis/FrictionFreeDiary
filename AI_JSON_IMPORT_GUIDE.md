# 🤖 AI Agent Guide: Formatting JSON for FrictionFree Diary (FlowDiary)

This guide provides the exact JSON schema, field specifications, and formatting rules required for an **AI Agent**, LLM, or automated script to generate valid data files that can be imported directly into **FrictionFree Diary**.

---

## 📥 How Users Import This File
Users import the generated JSON into the app by:
1. Opening **FrictionFree Diary**.
2. Navigating to **Settings** (gear icon in the bottom navigation).
3. Tapping **"Import from JSON"** under **Backup & Portability**.
4. Selecting the generated `.json` file from device storage.

---

## 📐 Root JSON Structure

The root of the file must be a JSON object containing:
- `version`: Integer (`1`).
- `app`: String (`"FrictionFreeDiary"`).
- `exportedAt`: Long integer (Unix epoch timestamp in milliseconds).
- `entries`: Array of `DiaryEntry` objects (required, can contain 1 to thousands of entries).
- `notebooks`: Array of `Notebook` objects (optional, default notebooks are created automatically if omitted or empty).
- `tags`: Array of `Tag` objects (optional, tags are automatically extracted and indexed from entries).

```json
{
  "version": 1,
  "app": "FrictionFreeDiary",
  "exportedAt": 1757268000000,
  "entries": [
    ...
  ],
  "notebooks": [
    ...
  ],
  "tags": [
    ...
  ]
}
```

---

## 📝 `DiaryEntry` Specification

Each item in the `"entries"` array represents an individual journal entry:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | `String` | **Yes** | — | Unique identifier. **Must be a UUID v4 string** (e.g. `"550e8400-e29b-41d4-a716-446655440000"`). |
| `title` | `String` | No | `""` | Title of the entry. Can be left empty `""` for untitled quick thoughts. |
| `content` | `String` | **Yes** | `""` | Full Markdown text body. Supports headers, bold, italics, checklists, quotes, and inline `#hashtags`. |
| `notebookId` | `String` | No | `"default_personal"` | Target notebook ID. Use built-in IDs or match a custom notebook defined in `"notebooks"`. |
| `colorHex` | `String` | No | `"#00000000"` | Color coding accent hex. Use one of the curated palettes below or `"#00000000"` for None. |
| `createdAt` | `Long` | **Yes** | (now) | Creation timestamp in **Unix epoch milliseconds** (e.g. `1757257200000`). |
| `updatedAt` | `Long` | No | (now) | Last modified timestamp in **Unix epoch milliseconds**. |
| `latitude` | `Double?` | No | `null` | GPS latitude (e.g. `40.7608`). |
| `longitude` | `Double?` | No | `null` | GPS longitude (e.g. `-111.8910`). |
| `locationName` | `String?` | No | `null` | Human-readable place name (e.g. `"Salt Lake City, Utah"`). |
| `mediaUris` | `Array<String>` | No | `[]` | Local device file paths to photos. Use `[]` when generating text entries. |
| `tags` | `Array<String>` | No | `[]` | Explicit tags without the `#` prefix (e.g. `["ideas", "gratitude"]`). |
| `isPinned` | `Boolean` | No | `false` | Set to `true` to pin this entry to the top of the stream. |
| `isFavorite` | `Boolean` | No | `false` | Set to `true` to mark entry as starred / favorite. |
| `moodEmoji` | `String?` | No | `null` | Single emoji representing the mood (e.g. `"😊"`, `"🚀"`, `"🌧️"`). |
| `isArchived` | `Boolean` | No | `false` | Set to `true` to place the entry directly into the notebook's archive. |

---

## 🎨 Supported Color Accents (`colorHex`)

FrictionFree Diary uses a curated palette of calming accents:

| Name | Hex Value | Note |
|---|---|---|
| **None (Default)** | `"#00000000"` | Standard neutral card |
| **Emerald Green** | `"#2E7D32"` | Calming growth, health |
| **Ocean Blue** | `"#1565C0"` | Deep focus, clarity |
| **Sunset Orange** | `"#E65100"` | Energy, creativity |
| **Lavender Purple**| `"#6A1B9A"` | Reflection, mindfulness |
| **Cherry Rose** | `"#C2185B"` | Passion, relationships |
| **Amber Gold** | `"#F57F17"` | Gratitude, warmth |
| **Teal Calm** | `"#00695C"` | Serenity, balance |
| **Slate Grey** | `"#37474F"` | Work, structured notes |

---

## 📚 Notebook Organization (`notebookId`)

### Built-in Default Notebook IDs
These notebooks always exist in the app:
* `"default_personal"` - **Personal** (Default destination for general entries)
* `"default_ideas"` - **Ideas** (Brainstorms, inventions, sparks)
* `"default_reflections"` - **Reflections** (Deep thoughts, gratitude, life lessons)

### Custom Notebooks
To create new notebooks, define them in the `"notebooks"` array and use their `id` in `entry.notebookId`:

```json
{
  "id": "notebook_reading_log",
  "name": "Reading & Books",
  "description": "Book reviews, quotes, and takeaways",
  "icon": "book",
  "colorHex": "#00695C",
  "createdAt": 1757268000000,
  "isDefault": false
}
```

---

## ✍️ Markdown Formatting in `content`

The app's native Markdown engine renders rich styling:

- **Headings**: `# Title`, `## Subheading`, `### Section`
- **Bold & Italics**: `**bold text**`, `*italic text*`
- **Interactive Checklists**: `- [ ] Todo item`, `- [x] Completed item`
- **Bullet & Numbered Lists**: `- Bullet 1`, `1. Numbered item`
- **Blockquotes**: `> Inspiring quote or excerpt`
- **Code Blocks**: \`\`\`kotlin ... \`\`\` or inline \`code\`
- **Hashtags**: Any `#word` placed in `content` or `title` is dynamically parsed and indexed into the app's interactive tag cloud.
- **Timestamps**: Formatting like `**9:30 AM** - Started morning review` is supported and highlighted.
- **Hyperlinks**: `[Display Text](https://example.com)` or `[Display Text](<https://example.com>)`

---

## 💡 Minimal Valid JSON Example

An AI agent generating a single quick journal entry can output as little as this:

```json
{
  "version": 1,
  "app": "FrictionFreeDiary",
  "exportedAt": 1757268000000,
  "entries": [
    {
      "id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
      "title": "Morning Reflections",
      "content": "Woke up early today and went for a brisk walk. #morning #gratitude\n\n- [x] 20 min morning walk\n- [ ] Finish project draft\n\n> The journey of a thousand miles begins with a single step.",
      "notebookId": "default_personal",
      "colorHex": "#2E7D32",
      "createdAt": 1757257200000,
      "updatedAt": 1757257200000,
      "tags": ["morning", "gratitude"],
      "isPinned": false,
      "isFavorite": true
    }
  ],
  "notebooks": [],
  "tags": []
}
```

---

## 🌟 Comprehensive Multi-Entry Example

Here is a full example with multiple entries across custom notebooks, tags, colors, and coordinates:

```json
{
  "version": 1,
  "app": "FrictionFreeDiary",
  "exportedAt": 1757268000000,
  "entries": [
    {
      "id": "11111111-2222-3333-4444-555555555555",
      "title": "Product Strategy Session",
      "content": "Brainstorming architecture for our local-first offline storage. #engineering #architecture\n\n### Key Takeaways\n- Never compromise user privacy.\n- Zero network permissions guarantees mathematical security.\n\n```kotlin\nval isOffline = true\nassert(isOffline)\n```",
      "notebookId": "notebook_work_projects",
      "colorHex": "#1565C0",
      "createdAt": 1757250000000,
      "updatedAt": 1757253600000,
      "latitude": 37.7749,
      "longitude": -122.4194,
      "locationName": "San Francisco, CA",
      "mediaUris": [],
      "tags": ["engineering", "architecture"],
      "isPinned": true,
      "isFavorite": false,
      "moodEmoji": "💡",
      "isArchived": false
    },
    {
      "id": "66666666-7777-8888-9999-000000000000",
      "title": "Evening Gratitude",
      "content": "Grateful for quiet evenings, hot chamomile tea, and family dinner. #gratitude #family",
      "notebookId": "default_reflections",
      "colorHex": "#F57F17",
      "createdAt": 1757286000000,
      "updatedAt": 1757286000000,
      "latitude": null,
      "longitude": null,
      "locationName": null,
      "mediaUris": [],
      "tags": ["gratitude", "family"],
      "isPinned": false,
      "isFavorite": true,
      "moodEmoji": "🙏",
      "isArchived": false
    }
  ],
  "notebooks": [
    {
      "id": "notebook_work_projects",
      "name": "Work & Engineering",
      "description": "Professional engineering and project logs",
      "icon": "work",
      "colorHex": "#1565C0",
      "createdAt": 1757200000000,
      "isDefault": false
    }
  ],
  "tags": [
    { "name": "engineering", "usageCount": 1, "lastUsedAt": 1757250000000 },
    { "name": "architecture", "usageCount": 1, "lastUsedAt": 1757250000000 },
    { "name": "gratitude", "usageCount": 1, "lastUsedAt": 1757286000000 },
    { "name": "family", "usageCount": 1, "lastUsedAt": 1757286000000 }
  ]
}
```

---

## 🛠️ Instructions for AI Agents (System Prompt Snippet)

When a user asks you (the AI) to convert documents, chat transcripts, voice notes, or text files into FrictionFree Diary JSON format, follow these strict rules:

1. **Output Valid JSON Only**: Ensure proper escaping of newlines (`\n`), quotation marks (`\"`), and backslashes in Markdown text.
2. **Generate Valid UUIDs**: Every entry must have a unique `id` in UUID v4 format (`xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`).
3. **Convert Dates to Epoch Milliseconds**: Convert any human-readable dates (e.g. `2026-09-07T14:30:00Z` or `September 7, 2026 2:30 PM`) into **Unix epoch milliseconds integer** (e.g. `1788791400000`).
4. **Preserve Hashtags**: Extract keywords into `tags` array and/or place `#hashtags` naturally in the `content`.
5. **Assign Suitable Colors**: Use the 8 curated color hex codes matching the mood/topic (e.g. Blue for work, Orange/Gold for gratitude, Green for health/personal).
6. **File Extension**: Instruct the user to save the generated file with a `.json` extension (e.g. `diary_import.json`).
