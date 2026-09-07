package com.frictionfree.diary.utils

import android.content.Context
import android.net.Uri
import com.frictionfree.diary.data.model.ExportData
import com.frictionfree.diary.data.repository.DiaryRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed class ImportOutcome {
    data class NativeSuccess(val entryCount: Int) : ImportOutcome()
    data class DayOneSuccess(val entryCount: Int, val notebookName: String, val photoCount: Int) : ImportOutcome()
    data class FacebookSuccess(val entryCount: Int, val notebookName: String, val photoCount: Int) : ImportOutcome()
    data class Error(val message: String) : ImportOutcome()
}

object JsonExporter {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToJsonString(data: ExportData): String {
        return json.encodeToString(data)
    }

    fun parseFromJsonString(jsonString: String): ExportData {
        return json.decodeFromString(jsonString)
    }

    fun writeExportFile(context: Context, data: ExportData): File {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val filename = "frictionfree_diary_export_${System.currentTimeMillis()}.json"
        val file = File(exportDir, filename)
        file.writeText(exportToJsonString(data), Charsets.UTF_8)
        return file
    }

    /**
     * Unified importer supporting native FrictionFree Diary JSON backups,
     * Day One journal archives (.zip and .json), and Facebook data archives (.zip and .json).
     */
    suspend fun importBackupOrArchive(
        context: Context,
        uri: Uri,
        diaryRepository: DiaryRepository,
        targetType: String? = null,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)? = null
    ): ImportOutcome {
        return try {
            if (isZipFile(context, uri)) {
                val isFb = when (targetType) {
                    "Facebook" -> true
                    "DayOne" -> false
                    else -> isFacebookZipArchive(context, uri)
                }
                if (isFb) {
                    val fbResult = FacebookImporter.importZip(context, uri, diaryRepository, onProgress)
                    ImportOutcome.FacebookSuccess(
                        entryCount = fbResult.entryCount,
                        notebookName = fbResult.notebookName,
                        photoCount = fbResult.photoCount
                    )
                } else {
                    val dayOneResult = DayOneImporter.importZip(context, uri, diaryRepository, onProgress)
                    ImportOutcome.DayOneSuccess(
                        entryCount = dayOneResult.entryCount,
                        notebookName = dayOneResult.notebookName,
                        photoCount = dayOneResult.photoCount
                    )
                }
            } else {
                onProgress?.invoke("Reading File", "Loading backup contents...", null)
                // Read text content
                val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                    reader.readText()
                } ?: return ImportOutcome.Error("Unable to open selected file.")

                if (targetType == "DayOne" || (targetType == null && DayOneImporter.isDayOneJson(content))) {
                    val dayOneResult = DayOneImporter.importJsonContent(
                        jsonContent = content,
                        notebookName = "Day One Import",
                        photoPathMap = emptyMap(),
                        diaryRepository = diaryRepository,
                        onProgress = onProgress
                    )
                    ImportOutcome.DayOneSuccess(
                        entryCount = dayOneResult.entryCount,
                        notebookName = dayOneResult.notebookName,
                        photoCount = dayOneResult.photoCount
                    )
                } else if (targetType == "Facebook" || (targetType == null && FacebookImporter.isFacebookJson(content))) {
                    val fbResult = FacebookImporter.importJsonContent(
                        jsonContent = content,
                        notebookName = "Facebook",
                        photoPathMap = emptyMap(),
                        diaryRepository = diaryRepository,
                        onProgress = onProgress
                    )
                    ImportOutcome.FacebookSuccess(
                        entryCount = fbResult.entryCount,
                        notebookName = fbResult.notebookName,
                        photoCount = fbResult.photoCount
                    )
                } else {
                    val nativeData = parseFromJsonString(content)
                    diaryRepository.importData(nativeData, overwriteExisting = false, onProgress = onProgress)
                    ImportOutcome.NativeSuccess(entryCount = nativeData.entries.size)
                }
            }
        } catch (e: Exception) {
            ImportOutcome.Error("Import failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun isFacebookZipArchive(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                java.util.zip.ZipInputStream(stream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (name.contains("your_posts") || name.contains("your_facebook_activity") || name.contains("facebook")) {
                            return true
                        }
                        entry = zipStream.nextEntry
                    }
                    false
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun isZipFile(context: Context, uri: Uri): Boolean {
        // 1. Check filename/URI path
        val uriPath = uri.toString().lowercase()
        if (uriPath.endsWith(".zip")) return true

        // 2. Check MIME type
        val mime = context.contentResolver.getType(uri)?.lowercase()
        if (mime != null && mime.contains("zip")) return true

        // 3. Check ZIP magic bytes (0x50, 0x4B) -> 'P', 'K'
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun readFromUri(context: Context, uri: Uri): ExportData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val content = reader.readText()
                parseFromJsonString(content)
            }
        } catch (_: Exception) {
            null
        }
    }
}
