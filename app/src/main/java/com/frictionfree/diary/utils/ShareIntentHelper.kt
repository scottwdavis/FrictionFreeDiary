package com.frictionfree.diary.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ShareIntentHelper {

    data class ParsedShareData(
        val title: String,
        val content: String,
        val mediaUris: List<String>
    )

    fun parseIncomingIntent(context: Context, intent: Intent): ParsedShareData? {
        val action = intent.action
        val type = intent.type ?: return null

        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }

        var title = ""
        var content = ""
        val mediaPaths = mutableListOf<String>()

        // Check for text / calendar share
        if (type.startsWith("text/")) {
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

            if (!subject.isNullOrBlank()) {
                title = subject
            }

            // Detect calendar event formatting
            if (isCalendarEventText(text)) {
                val formatted = parseCalendarEvent(text, subject)
                if (title.isBlank()) title = formatted.title
                content = formatted.content
            } else {
                content = text
                if (title.isBlank() && text.isNotBlank()) {
                    // Use first line or first 40 chars as title
                    val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: ""
                    title = if (firstLine.length > 40) firstLine.take(40) + "..." else firstLine
                }
            }
        }

        // Check for image shares
        if (action == Intent.ACTION_SEND && type.startsWith("image/")) {
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (imageUri != null) {
                copyMediaToInternal(context, imageUri)?.let { mediaPaths.add(it) }
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE && type.startsWith("image/")) {
            val imageUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            imageUris?.forEach { uri ->
                copyMediaToInternal(context, uri)?.let { mediaPaths.add(it) }
            }
        }

        return ParsedShareData(title, content, mediaPaths)
    }

    private fun isCalendarEventText(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("event:") || lower.contains("meeting:") || lower.contains("when:") || lower.contains("organizer:"))
    }

    private fun parseCalendarEvent(rawText: String, subject: String?): ParsedShareData {
        val lines = rawText.lines()
        var title = subject ?: ""
        val builder = StringBuilder()

        builder.append("### 📅 Calendar Event Note\n\n")

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Event:", ignoreCase = true) -> {
                    if (title.isBlank()) title = trimmed.removePrefix("Event:").trim()
                    builder.append("**Event**: ${trimmed.removePrefix("Event:").trim()}\n")
                }
                trimmed.startsWith("When:", ignoreCase = true) -> {
                    builder.append("**When**: ${trimmed.removePrefix("When:").trim()}\n")
                }
                trimmed.startsWith("Where:", ignoreCase = true) -> {
                    builder.append("**Where**: ${trimmed.removePrefix("Where:").trim()}\n")
                }
                trimmed.startsWith("Organizer:", ignoreCase = true) -> {
                    builder.append("**Organizer**: ${trimmed.removePrefix("Organizer:").trim()}\n")
                }
                trimmed.isNotBlank() -> {
                    builder.append("$trimmed\n")
                }
            }
        }

        builder.append("\n---\n*Shared from Calendar to FrictionFree Diary*\n")
        if (title.isBlank()) title = "Calendar Event"

        return ParsedShareData(title, builder.toString(), emptyList())
    }

    fun copyMediaToInternal(context: Context, sourceUri: Uri): String? {
        return try {
            val mediaDir = File(context.filesDir, "media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            val destFile = File(mediaDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
