"""Check authored imports, native exports, or portable backup ZIP packages. Python standard library only.

Default profile is intentionally stricter than Kotlin decoding; see native-format.md.
No files are changed. Exit 0 = no errors, 1 = invalid, 2 = CLI usage error.
"""
import argparse
import json
import math
import re
import sys
import uuid
import zipfile
from pathlib import Path

COLORS = {"#00000000", "#2E7D32", "#1565C0", "#E65100", "#6A1B9A",
          "#C2185B", "#F57F17", "#00695C", "#37474F"}
ICONS = {"book", "lightbulb", "spa", "favorite", "star", "work", "school",
         "flight", "fitness_center", "palette", "home", "code", "music_note",
         "pets", "shopping_cart"}
ROOT = {"version": "int", "app": "str", "exportedAt": "long",
        "entries": "list", "notebooks": "list", "tags": "list"}
ENTRY = {"id": "str", "title": "str", "content": "str", "notebookId": "str",
         "colorHex": "str", "createdAt": "long", "updatedAt": "long",
         "latitude": "number?", "longitude": "number?", "locationName": "str?",
         "mediaUris": "strings", "tags": "strings", "isPinned": "bool",
         "isFavorite": "bool", "moodEmoji": "str?", "isArchived": "bool"}
NOTEBOOK = {"id": "str", "name": "str", "description": "str", "icon": "str",
            "colorHex": "str", "createdAt": "long", "isDefault": "bool"}
TAG = {"name": "str", "usageCount": "int", "lastUsedAt": "long"}

BACKUP_JSON_NAME = "frictionfree-backup.json"
MAX_ZIP_ASSET_COUNT = 5000
MAX_ZIP_UNCOMPRESSED_BYTES = 2 * 1024 * 1024 * 1024  # 2 GB


def type_ok(value, kind):
    if kind.endswith("?"):
        return value is None or type_ok(value, kind[:-1])
    if kind == "str":
        return isinstance(value, str)
    if kind == "bool":
        return type(value) is bool
    if kind in ("int", "long"):
        bits = 32 if kind == "int" else 64
        return type(value) is int and -(2 ** (bits - 1)) <= value < 2 ** (bits - 1)
    if kind == "number":
        if type(value) not in (int, float):
            return False
        try:
            return math.isfinite(value)
        except OverflowError:
            return False
    if kind == "list":
        return isinstance(value, list)
    if kind == "strings":
        return isinstance(value, list) and all(isinstance(v, str) for v in value)
    raise ValueError(kind)


def validate_image_bytes(data: bytes) -> bool:
    """Validate image bytes using standard magic bytes (JPEG, PNG, GIF, WebP, BMP)."""
    if len(data) < 4:
        return False
    # JPEG: FF D8 FF
    if data.startswith(b"\xff\xd8\xff"):
        return True
    # PNG: 89 50 4E 47 0D 0A 1A 0A
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return True
    # GIF: GIF87a or GIF89a
    if data.startswith(b"GIF87a") or data.startswith(b"GIF89a"):
        return True
    # WebP: RIFF .... WEBP
    if data.startswith(b"RIFF") and len(data) >= 12 and data[8:12] == b"WEBP":
        return True
    # BMP: BM
    if data.startswith(b"BM"):
        return True
    return False


def validate(data, native_export=False, existing_notebooks=(), is_package=False, package_files=None):
    errors, warnings = [], []

    def check_object(obj, schema, required, path):
        if not isinstance(obj, dict):
            errors.append(f"{path}: expected object")
            return False
        for field in required:
            if field not in obj:
                errors.append(f"{path}.{field}: missing field")
        for field, value in obj.items():
            if field not in schema:
                errors.append(f"{path}.{field}: unknown field (app would discard it)")
            elif not type_ok(value, schema[field]):
                errors.append(f"{path}.{field}: expected {schema[field]}")
        return True

    if not check_object(data, ROOT, () if native_export else ROOT, "root"):
        return errors, warnings
    if data.get("version", 1) != 1:
        errors.append("root.version: expected 1 for this contract")
    if data.get("app", "FrictionFreeDiary") != "FrictionFreeDiary":
        errors.append("root.app: expected FrictionFreeDiary")

    def objects(field):
        value = data.get(field, [])
        return value if isinstance(value, list) else []

    notebook_ids = set(existing_notebooks)
    seen_notebooks, seen_entries = set(), set()
    for i, notebook in enumerate(objects("notebooks")):
        path = f"notebooks[{i}]"
        if not check_object(notebook, NOTEBOOK, ("id", "name") if native_export else NOTEBOOK, path):
            continue
        notebook_id = notebook.get("id")
        if isinstance(notebook_id, str):
            if not notebook_id.strip():
                errors.append(f"{path}.id: must not be blank")
            elif notebook_id in seen_notebooks:
                errors.append(f"{path}.id: duplicate notebook ID")
            seen_notebooks.add(notebook_id)
            notebook_ids.add(notebook_id)
        if isinstance(notebook.get("name"), str) and not notebook["name"].strip():
            errors.append(f"{path}.name: must not be blank")
        if isinstance(notebook.get("icon"), str) and notebook["icon"] not in ICONS:
            warnings.append(f"{path}.icon: unknown icon falls back to book")
        color = notebook.get("colorHex")
        if isinstance(color, str) and not re.fullmatch(r"#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})", color):
            errors.append(f"{path}.colorHex: expected #RRGGBB or #AARRGGBB")

    referenced_package_media = set()

    for i, entry in enumerate(objects("entries")):
        path = f"entries[{i}]"
        required = ("id",) if native_export else ("id", "content", "notebookId", "createdAt", "updatedAt")
        if not check_object(entry, ENTRY, required, path):
            continue
        entry_id = entry.get("id")
        if isinstance(entry_id, str):
            if not entry_id.strip():
                errors.append(f"{path}.id: blank ID would be replaced on import")
            if entry_id in seen_entries:
                errors.append(f"{path}.id: duplicate entry ID")
            seen_entries.add(entry_id)
            if not native_export:
                try:
                    parsed = uuid.UUID(entry_id)
                    if parsed.version != 4 or str(parsed) != entry_id.lower():
                        raise ValueError()
                except ValueError:
                    errors.append(f"{path}.id: newly authored IDs must be canonical UUID v4")
        notebook_id = entry.get("notebookId", "default_personal")
        if isinstance(notebook_id, str) and notebook_id not in notebook_ids:
            message = f"{path}.notebookId: not defined in file or --existing-notebook"
            (warnings if native_export else errors).append(message)
        for field in ("createdAt", "updatedAt"):
            stamp = entry.get(field)
            if type(stamp) is int and -(2 ** 63) <= stamp < 2 ** 63:
                if field == "createdAt" and stamp <= 0:
                    errors.append(f"{path}.{field}: app replaces nonpositive creation time with now")
                elif 1_000_000_000 <= stamp < 10_000_000_000:
                    warnings.append(f"{path}.{field}: possible epoch seconds; confirm intended 1970 date or multiply seconds by 1000")
        created, updated = entry.get("createdAt"), entry.get("updatedAt")
        if type(created) is int and type(updated) is int and updated < created:
            warnings.append(f"{path}.updatedAt: earlier than createdAt")
        color = entry.get("colorHex", "#00000000")
        if isinstance(color, str) and color.upper() not in COLORS:
            errors.append(f"{path}.colorHex: use a supported entry palette color")
        for field, limit in (("latitude", 90), ("longitude", 180)):
            value = entry.get(field)
            if value is not None and type_ok(value, "number") and not -limit <= value <= limit:
                errors.append(f"{path}.{field}: outside geographic range")
        if (entry.get("latitude") is None) != (entry.get("longitude") is None):
            warnings.append(f"{path}: only one coordinate provided")
        tags = entry.get("tags", [])
        if type_ok(tags, "strings"):
            if any(not t or t != t.strip().lower() or t.startswith("#") for t in tags):
                errors.append(f"{path}.tags: use nonblank lowercase trimmed tags without #")
            if len(tags) != len(set(tags)):
                errors.append(f"{path}.tags: duplicate tags")

        media_uris = entry.get("mediaUris", [])
        if type_ok(media_uris, "strings") and media_uris:
            if is_package:
                for m_idx, media_path in enumerate(media_uris):
                    norm = media_path.replace("\\", "/").lstrip("/")
                    if not norm.startswith("media/"):
                        errors.append(f"{path}.mediaUris[{m_idx}]: package media path must start with 'media/'")
                    else:
                        referenced_package_media.add(norm)
                        if package_files is not None and norm not in package_files:
                            errors.append(f"{path}.mediaUris[{m_idx}]: referenced file '{norm}' missing from ZIP package")
            else:
                warnings.append(f"{path}.mediaUris: standalone JSON import does not copy media; confirm every path works on target Android device")

    if is_package and package_files is not None:
        media_files_in_zip = {f for f in package_files if f.startswith("media/") and not f.endswith("/")}
        unreferenced = media_files_in_zip - referenced_package_media
        if unreferenced:
            warnings.append(f"Package contains unreferenced media file(s): {', '.join(sorted(unreferenced))}")

    for i, tag in enumerate(objects("tags")):
        check_object(tag, TAG, ("name",), f"tags[{i}]")
    if objects("tags"):
        warnings.append("root.tags metadata is ignored by native import; put tags on entries")
    if not objects("entries"):
        warnings.append("No entries in file")
    return errors, warnings


def reject_constant(value):
    raise ValueError(f"Non-JSON number: {value}")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def parse_json_content(content: str):
    return json.loads(content, parse_constant=reject_constant, object_pairs_hook=unique_object)


def load_json(path):
    return parse_json_content(Path(path).read_text(encoding="utf-8"))


def validate_zip_package(zip_path: Path, native_export=False, existing_notebooks=()):
    errors, warnings = [], []
    package_files = set()
    total_uncompressed = 0

    try:
        with zipfile.ZipFile(zip_path, "r") as zf:
            infolist = zf.infolist()

            if len(infolist) > MAX_ZIP_ASSET_COUNT:
                errors.append(f"Package contains {len(infolist)} entries, exceeding limit ({MAX_ZIP_ASSET_COUNT})")
                return errors, warnings

            json_entry = None
            for info in infolist:
                norm_name = info.filename.replace("\\", "/")

                # Path traversal / safety checks
                if norm_name.startswith("/") or any(part in ("..", ".") for part in norm_name.split("/") if part):
                    errors.append(f"Unsafe path in ZIP archive: '{info.filename}'")
                    continue

                if norm_name in package_files:
                    errors.append(f"Duplicate entry in ZIP archive: '{info.filename}'")
                    continue
                package_files.add(norm_name)

                total_uncompressed += info.file_size
                if total_uncompressed > MAX_ZIP_UNCOMPRESSED_BYTES:
                    errors.append(f"Package uncompressed size exceeds limit of 2 GB")
                    return errors, warnings

                if norm_name == BACKUP_JSON_NAME:
                    json_entry = info

            if not json_entry:
                errors.append(f"Package missing required root file '{BACKUP_JSON_NAME}'")
                return errors, warnings

            # Validate all media images in ZIP
            for info in infolist:
                norm_name = info.filename.replace("\\", "/")
                if norm_name.startswith("media/") and not info.is_dir():
                    img_data = zf.read(info)
                    if not validate_image_bytes(img_data):
                        errors.append(f"Image '{norm_name}' is corrupt or not a recognized image format")

            # Read and parse JSON
            json_bytes = zf.read(json_entry)
            try:
                data = parse_json_content(json_bytes.decode("utf-8"))
            except Exception as exc:
                errors.append(f"Invalid {BACKUP_JSON_NAME}: {exc}")
                return errors, warnings

            json_errors, json_warnings = validate(
                data,
                native_export=native_export,
                existing_notebooks=existing_notebooks,
                is_package=True,
                package_files=package_files
            )
            errors.extend(json_errors)
            warnings.extend(json_warnings)
            return errors, warnings, data
    except zipfile.BadZipFile as exc:
        errors.append(f"Invalid ZIP archive: {exc}")
        return errors, warnings
    except Exception as exc:
        errors.append(f"Error reading package: {exc}")
        return errors, warnings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path)
    parser.add_argument("--native-export", action="store_true",
                        help="Allow model defaults and existing non-UUID IDs")
    parser.add_argument("--existing-notebook", action="append", default=[], metavar="ID",
                        help="Notebook confirmed to exist on target device; may repeat")
    args = parser.parse_args()

    target_path = args.file
    if not target_path.exists():
        print(f"ERROR: File not found: {target_path}")
        return 1

    is_zip = zipfile.is_zipfile(target_path) or target_path.suffix.lower() == ".zip"
    data = None

    if is_zip:
        res = validate_zip_package(target_path, args.native_export, args.existing_notebook)
        if len(res) == 3:
            errors, warnings, data = res
        else:
            errors, warnings = res
    else:
        try:
            data = load_json(target_path)
            errors, warnings = validate(data, args.native_export, args.existing_notebook, is_package=False)
        except (OSError, UnicodeError, ValueError, OverflowError) as exc:
            errors, warnings = [str(exc)], []

    for message in warnings:
        print(f"WARNING: {message}")
    for message in errors:
        print(f"ERROR: {message}")
    if errors:
        print(f"INVALID: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1

    entry_count = len(data.get("entries", [])) if data else 0
    notebook_count = len(data.get("notebooks", [])) if data else 0
    package_desc = "ZIP package" if is_zip else "JSON file"
    print(f"VALID {package_desc}: {entry_count} entries, "
          f"{notebook_count} notebooks, {len(warnings)} warning(s). "
          "Android device import has not been executed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
