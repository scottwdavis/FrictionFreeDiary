package com.frictionfree.diary.utils

import android.content.Context
import android.net.Uri
import com.frictionfree.diary.data.model.ExportData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

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
