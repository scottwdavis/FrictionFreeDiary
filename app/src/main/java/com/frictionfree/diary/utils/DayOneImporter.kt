package com.frictionfree.diary.utils

import android.content.Context
import android.net.Uri
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.EntryColor
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.repository.DiaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipInputStream

@Serializable
data class DayOneExport(
    val metadata: DayOneMetadata? = null,
    val entries: List<DayOneEntry> = emptyList()
)

@Serializable
data class DayOneMetadata(
    val version: String? = null
)

@Serializable
data class DayOneEntry(
    val uuid: String? = null,
    val text: String? = null,
    val creationDate: String? = null,
    val modifiedDate: String? = null,
    val timeZone: String? = null,
    val tags: List<String> = emptyList(),
    val starred: Boolean = false,
    val isPinned: Boolean = false,
    val location: DayOneLocation? = null,
    val photos: List<DayOnePhoto> = emptyList()
)

@Serializable
data class DayOneLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
    val localityName: String? = null,
    val administrativeArea: String? = null,
    val country: String? = null,
    val address: String? = null
)

@Serializable
data class DayOnePhoto(
    val identifier: String? = null,
    val md5: String? = null,
    val type: String? = null
)

data class DayOneImportResult(
    val entryCount: Int,
    val notebookName: String,
    val photoCount: Int
)

object DayOneImporter {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Checks if a JSON string matches Day One's schema
     */
    fun isDayOneJson(jsonString: String): Boolean {
        return jsonString.contains("\"entries\"") &&
                (jsonString.contains("\"creationDate\"") || jsonString.contains("\"uuid\""))
    }

    /**
     * Imports from a Day One ZIP archive containing JSON and optional photos
     */
    suspend fun importZip(
        context: Context,
        zipUri: Uri,
        diaryRepository: DiaryRepository
    ): DayOneImportResult = withContext(Dispatchers.IO) {
        var jsonContent: String? = null
        var jsonFilename: String? = null
        val extractedPhotosMap = mutableMapOf<String, String>() // filename/md5 -> local absolute file path

        val mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) mediaDir.mkdirs()

        context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zipStream ->
                var zipEntry = zipStream.nextEntry
                while (zipEntry != null) {
                    val entryName = zipEntry.name
                    val fileName = File(entryName).name

                    if (!zipEntry.isDirectory) {
                        if (entryName.endsWith(".json", ignoreCase = true)) {
                            jsonFilename = fileName
                            val buffer = ByteArrayOutputStream()
                            val data = ByteArray(4096)
                            var count: Int
                            while (zipStream.read(data).also { count = it } != -1) {
                                buffer.write(data, 0, count)
                            }
                            jsonContent = buffer.toString("UTF-8")
                        } else if (entryName.contains("photos", ignoreCase = true) ||
                            fileName.endsWith(".jpg", ignoreCase = true) ||
                            fileName.endsWith(".jpeg", ignoreCase = true) ||
                            fileName.endsWith(".png", ignoreCase = true)
                        ) {
                            // Extract photo to media dir
                            val destFile = File(mediaDir, "dayone_${UUID.randomUUID().toString().take(8)}_$fileName")
                            FileOutputStream(destFile).use { output ->
                                zipStream.copyTo(output)
                            }
                            extractedPhotosMap[fileName] = destFile.absolutePath
                            // Also map by name without extension if md5 is used
                            val nameWithoutExt = fileName.substringBeforeLast(".")
                            extractedPhotosMap[nameWithoutExt] = destFile.absolutePath
                        }
                    }
                    zipStream.closeEntry()
                    zipEntry = zipStream.nextEntry
                }
            }
        }

        if (jsonContent.isNullOrBlank()) {
            throw IllegalArgumentException("No valid Day One JSON file found in ZIP archive.")
        }

        // Derive notebook name from JSON filename: e.g. "Come Follow Me .json" -> "Come Follow Me"
        val derivedNotebookName = jsonFilename
            ?.removeSuffix(".json")
            ?.removeSuffix(".JSON")
            ?.trim()
            ?.ifBlank { "Day One Import" }
            ?: "Day One Import"

        importJsonContent(
            jsonContent = jsonContent!!,
            notebookName = derivedNotebookName,
            photoPathMap = extractedPhotosMap,
            diaryRepository = diaryRepository
        )
    }

    /**
     * Imports from Day One JSON content directly
     */
    suspend fun importJsonContent(
        jsonContent: String,
        notebookName: String,
        photoPathMap: Map<String, String> = emptyMap(),
        diaryRepository: DiaryRepository
    ): DayOneImportResult = withContext(Dispatchers.IO) {
        val dayOneExport = jsonParser.decodeFromString<DayOneExport>(jsonContent)

        // 1. Create or find Notebook
        val allNotebooks = diaryRepository.getAllNotebooks()
        val notebookId = "nb_dayone_${UUID.randomUUID().toString().take(8)}"
        val notebook = Notebook(
            id = notebookId,
            name = notebookName,
            description = "Imported from Day One",
            icon = "book",
            colorHex = "#1565C0",
            isDefault = false
        )
        diaryRepository.saveNotebook(notebook)

        var photoCount = 0

        // 2. Map and save entries
        dayOneExport.entries.forEach { entry ->
            val (title, content) = parseTitleAndContent(entry.text ?: "")

            val createdAt = parseIsoDate(entry.creationDate)
            val updatedAt = parseIsoDate(entry.modifiedDate) ?: createdAt

            // Location
            val lat = entry.location?.latitude
            val lon = entry.location?.longitude
            val locationName = buildLocationLabel(entry.location)

            // Photos mapping
            val mediaList = mutableListOf<String>()
            entry.photos.forEach { photo ->
                val key = photo.md5 ?: photo.identifier
                if (key != null && photoPathMap.containsKey(key)) {
                    photoPathMap[key]?.let {
                        mediaList.add(it)
                        photoCount++
                    }
                }
            }

            val diaryEntry = DiaryEntry(
                id = entry.uuid ?: UUID.randomUUID().toString(),
                title = title,
                content = content,
                notebookId = notebookId,
                colorHex = EntryColor.DEFAULT.hex,
                createdAt = createdAt ?: System.currentTimeMillis(),
                updatedAt = updatedAt,
                latitude = lat,
                longitude = lon,
                locationName = locationName,
                mediaUris = mediaList,
                tags = entry.tags,
                isPinned = entry.isPinned,
                isFavorite = entry.starred
            )

            diaryRepository.saveEntry(diaryEntry)
        }

        DayOneImportResult(
            entryCount = dayOneExport.entries.size,
            notebookName = notebookName,
            photoCount = photoCount
        )
    }

    /**
     * Day One entries usually have the title on the first line formatted as '# Title'.
     * Also unescapes Day One markdown escaping like '\&' -> '&'.
     */
    fun parseTitleAndContent(rawText: String): Pair<String, String> {
        val cleanText = rawText.replace("\\&", "&").trim()
        if (cleanText.isBlank()) return Pair("", "")

        val lines = cleanText.lines()
        val firstLine = lines.firstOrNull()?.trim() ?: ""

        return if (firstLine.startsWith("# ")) {
            val title = firstLine.removePrefix("# ").trim()
            val remainingContent = lines.drop(1).joinToString("\n").trimStart()
            Pair(title, remainingContent.ifBlank { firstLine })
        } else if (firstLine.startsWith("## ")) {
            val title = firstLine.removePrefix("## ").trim()
            val remainingContent = lines.drop(1).joinToString("\n").trimStart()
            Pair(title, remainingContent.ifBlank { firstLine })
        } else {
            // First line doesn't start with '#', check length for title
            if (firstLine.length <= 60 && lines.size > 1) {
                Pair(firstLine, lines.drop(1).joinToString("\n").trimStart())
            } else {
                Pair("", cleanText)
            }
        }
    }

    /**
     * Parses ISO 8601 UTC date string (e.g. 2023-11-22T04:23:40Z) to epoch milliseconds
     */
    fun parseIsoDate(dateString: String?): Long? {
        if (dateString.isNullOrBlank()) return null
        return try {
            Instant.parse(dateString).toEpochMilli()
        } catch (_: Exception) {
            try {
                // Fallback for custom formatted offsets
                val ta = DateTimeFormatter.ISO_DATE_TIME.parse(dateString)
                Instant.from(ta).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun buildLocationLabel(loc: DayOneLocation?): String? {
        if (loc == null) return null
        val parts = listOfNotNull(
            loc.placeName?.takeIf { it.isNotBlank() },
            loc.localityName?.takeIf { it.isNotBlank() }
        ).distinct()

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            loc.address?.takeIf { it.isNotBlank() }
        }
    }
}
