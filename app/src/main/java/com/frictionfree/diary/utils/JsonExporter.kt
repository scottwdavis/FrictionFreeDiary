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
     * Unified importer supporting both native FrictionFree Diary JSON backups
     * and Day One journal archives (.zip and .json).
     */
    suspend fun importBackupOrArchive(
        context: Context,
        uri: Uri,
        diaryRepository: DiaryRepository
    ): ImportOutcome {
        return try {
            if (isZipFile(context, uri)) {
                val dayOneResult = DayOneImporter.importZip(context, uri, diaryRepository)
                ImportOutcome.DayOneSuccess(
                    entryCount = dayOneResult.entryCount,
                    notebookName = dayOneResult.notebookName,
                    photoCount = dayOneResult.photoCount
                )
            } else {
                // Read text content
                val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                    reader.readText()
                } ?: return ImportOutcome.Error("Unable to open selected file.")

                if (DayOneImporter.isDayOneJson(content)) {
                    val dayOneResult = DayOneImporter.importJsonContent(
                        jsonContent = content,
                        notebookName = "Day One Import",
                        diaryRepository = diaryRepository
                    )
                    ImportOutcome.DayOneSuccess(
                        entryCount = dayOneResult.entryCount,
                        notebookName = dayOneResult.notebookName,
                        photoCount = dayOneResult.photoCount
                    )
                } else {
                    val nativeData = parseFromJsonString(content)
                    diaryRepository.importData(nativeData, overwriteExisting = false)
                    ImportOutcome.NativeSuccess(entryCount = nativeData.entries.size)
                }
            }
        } catch (e: Exception) {
            ImportOutcome.Error("Import failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun isZipFile(context: Context, uri: Uri): Boolean {
        // 1. Check filename/URI path
        val uriPath = uri.toString().lowercase()
        if (uriPath.endsWith(".zip")) return true

        // 2. Check ZIP magic bytes (0x50, 0x4B, 0x03, 0x04)
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
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
