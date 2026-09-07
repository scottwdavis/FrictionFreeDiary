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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Serializable
data class FacebookPost(
    val timestamp: Long? = null,
    val title: String? = null,
    val data: List<FacebookPostData> = emptyList(),
    val attachments: List<FacebookAttachment> = emptyList(),
    val tags: List<String> = emptyList()
)

@Serializable
data class FacebookPostData(
    val post: String? = null,
    val update_timestamp: Long? = null
)

@Serializable
data class FacebookAttachment(
    val data: List<FacebookAttachmentData> = emptyList()
)

@Serializable
data class FacebookAttachmentData(
    val media: FacebookMedia? = null,
    val external_context: FacebookExternalContext? = null,
    val text: String? = null
)

@Serializable
data class FacebookMedia(
    val uri: String? = null,
    val creation_timestamp: Long? = null,
    val title: String? = null,
    val description: String? = null
)

@Serializable
data class FacebookExternalContext(
    val url: String? = null,
    val name: String? = null
)

data class FacebookImportResult(
    val entryCount: Int,
    val notebookName: String,
    val photoCount: Int
)

object FacebookImporter {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Fixes Facebook's known Latin-1 / UTF-8 mojibake encoding bug.
     * E.g. "\u00e2\u0080\u0099" -> "’", "\u00f0\u009f\u0098\u008a" -> "😊".
     */
    fun repairFacebookEncoding(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        return try {
            val bytes = input.toByteArray(Charsets.ISO_8859_1)
            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.contains('\uFFFD') && !input.contains('\uFFFD')) input else decoded
        } catch (_: Exception) {
            input
        }
    }

    /**
     * Determines if a JSON string matches Facebook's export post schema.
     */
    fun isFacebookJson(jsonString: String): Boolean {
        val sample = jsonString.take(1500)
        return (sample.contains("\"timestamp\"") && (sample.contains("\"post\"") || sample.contains("\"attachments\""))) ||
                sample.contains("\"your_posts\"") ||
                sample.contains("\"status_updates\"")
    }

    /**
     * Checks if a ZIP file looks like a Facebook export archive.
     */
    fun isFacebookZip(zipFile: java.util.zip.ZipFile): Boolean {
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val name = entries.nextElement().name.lowercase()
            if (name.contains("your_posts") || name.contains("your_facebook_activity") || name.startsWith("posts/")) {
                return true
            }
        }
        return false
    }

    /**
     * Imports from a Facebook ZIP archive containing your_posts_*.json and media files.
     */
    suspend fun importZip(
        context: Context,
        zipUri: Uri,
        diaryRepository: DiaryRepository,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)? = null
    ): FacebookImportResult = withContext(Dispatchers.IO) {
        onProgress?.invoke("Importing Facebook Archive", "Reading ZIP archive...", null)

        val tempZipFile = File(context.cacheDir, "fb_temp_${System.currentTimeMillis()}.zip")
        val mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) mediaDir.mkdirs()

        val jsonContents = mutableListOf<String>()
        val photoPathMap = mutableMapOf<String, String>() // normalized relative uri / filename -> absolute file path

        try {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Cannot open selected Facebook archive.")

            onProgress?.invoke("Extracting Archive", "Extracting photos & posts...", null)

            java.util.zip.ZipFile(tempZipFile).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name
                    val fileName = File(entryName).name.lowercase()

                    if (!entry.isDirectory) {
                        if (fileName.endsWith(".json") && (fileName.contains("post") || entryName.contains("posts"))) {
                            zipFile.getInputStream(entry).use { inStream ->
                                val buffer = ByteArrayOutputStream()
                                inStream.copyTo(buffer)
                                jsonContents.add(buffer.toString("UTF-8"))
                            }
                        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                            fileName.endsWith(".png") || fileName.endsWith(".webp")
                        ) {
                            val destFile = File(mediaDir, "fb_${UUID.randomUUID().toString().take(8)}_$fileName")
                            zipFile.getInputStream(entry).use { inStream ->
                                FileOutputStream(destFile).use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                            // Map by full path and by basename
                            val normPath = entryName.replace('\\', '/').trimStart('/')
                            photoPathMap[normPath] = destFile.absolutePath
                            photoPathMap[fileName] = destFile.absolutePath
                        }
                    }
                }
            }
        } finally {
            if (tempZipFile.exists()) tempZipFile.delete()
        }

        if (jsonContents.isEmpty()) {
            throw IllegalArgumentException("No valid Facebook post JSON files found in archive.")
        }

        val allPosts = mutableListOf<FacebookPost>()
        for (content in jsonContents) {
            allPosts.addAll(parsePostsJson(content))
        }

        importPostsList(
            posts = allPosts,
            notebookName = "Facebook",
            photoPathMap = photoPathMap,
            diaryRepository = diaryRepository,
            onProgress = onProgress
        )
    }

    /**
     * Imports from Facebook JSON string directly.
     */
    suspend fun importJsonContent(
        jsonContent: String,
        notebookName: String = "Facebook",
        photoPathMap: Map<String, String> = emptyMap(),
        diaryRepository: DiaryRepository,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)? = null
    ): FacebookImportResult = withContext(Dispatchers.IO) {
        val posts = parsePostsJson(jsonContent)
        importPostsList(posts, notebookName, photoPathMap, diaryRepository, onProgress)
    }

    /**
     * Safely decodes a JSON string which can be a List<FacebookPost> or wrapped in an object.
     */
    fun parsePostsJson(jsonContent: String): List<FacebookPost> {
        val trimmed = jsonContent.trim()
        return try {
            if (trimmed.startsWith("[")) {
                jsonParser.decodeFromString<List<FacebookPost>>(trimmed)
            } else {
                // If wrapped in an object e.g. { "status_updates": [...] } or { "your_posts": [...] }
                @Serializable
                data class WrappedFacebookPosts(
                    val status_updates: List<FacebookPost> = emptyList(),
                    val your_posts: List<FacebookPost> = emptyList()
                )
                val wrapped = jsonParser.decodeFromString<WrappedFacebookPosts>(trimmed)
                wrapped.status_updates.ifEmpty { wrapped.your_posts }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun importPostsList(
        posts: List<FacebookPost>,
        notebookName: String,
        photoPathMap: Map<String, String>,
        diaryRepository: DiaryRepository,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)?
    ): FacebookImportResult {
        onProgress?.invoke("Setting Up Notebook", "Creating notebook '$notebookName'...", null)

        // 1. Create or retrieve Facebook notebook
        val allNotebooks = diaryRepository.getAllNotebooks()
        val notebookId = "nb_facebook"
        val existingNotebook = diaryRepository.getNotebookById(notebookId)
        if (existingNotebook == null) {
            val fbNotebook = Notebook(
                id = notebookId,
                name = notebookName,
                description = "Imported from Facebook",
                icon = "public",
                colorHex = "#1877F2",
                isDefault = false
            )
            diaryRepository.saveNotebook(fbNotebook)
        }

        var photoCount = 0
        val total = posts.size

        // 2. Map and save entries
        posts.forEachIndexed { index, post ->
            val rawPostText = post.data.mapNotNull { it.post }.firstOrNull() ?: ""
            val cleanPostText = repairFacebookEncoding(rawPostText).trim()
            val rawTitle = repairFacebookEncoding(post.title).trim()

            // Resolve photos from attachments
            val mediaUris = mutableListOf<String>()
            val externalLinks = mutableListOf<String>()

            post.attachments.forEach { attachment ->
                attachment.data.forEach { item ->
                    item.media?.uri?.let { uriStr ->
                        val normUri = uriStr.replace('\\', '/').trimStart('/')
                        val fileName = File(normUri).name.lowercase()
                        val localPath = photoPathMap[normUri] ?: photoPathMap[fileName]
                        if (localPath != null) {
                            mediaUris.add(localPath)
                            photoCount++
                        }
                    }
                    item.external_context?.url?.let { linkUrl ->
                        val linkName = repairFacebookEncoding(item.external_context.name).ifBlank { linkUrl }
                        externalLinks.add("[$linkName]($linkUrl)")
                    }
                }
            }

            // Construct content
            val contentBuilder = StringBuilder()
            if (cleanPostText.isNotEmpty()) {
                contentBuilder.append(cleanPostText)
            }
            if (externalLinks.isNotEmpty()) {
                if (contentBuilder.isNotEmpty()) contentBuilder.append("\n\n")
                contentBuilder.append(externalLinks.joinToString("\n"))
            }

            val finalContent = contentBuilder.toString().ifBlank {
                if (mediaUris.isNotEmpty()) "Shared photos" else rawTitle.ifBlank { "Facebook Post" }
            }

            // Title derivation: clean out generic "X updated his status" boilerplate
            val derivedTitle = deriveTitle(rawTitle, cleanPostText)

            // Timestamps: Facebook exports in Unix epoch seconds
            val epochSeconds = post.timestamp ?: (System.currentTimeMillis() / 1000L)
            val epochMillis = epochSeconds * 1000L

            val entry = DiaryEntry(
                id = "fb_${UUID.randomUUID().toString().take(12)}",
                title = derivedTitle,
                content = finalContent,
                notebookId = notebookId,
                colorHex = EntryColor.DEFAULT.hex,
                createdAt = epochMillis,
                updatedAt = epochMillis,
                mediaUris = mediaUris,
                tags = post.tags.map { repairFacebookEncoding(it) },
                isPinned = false,
                isFavorite = false
            )

            diaryRepository.saveEntry(entry)

            if (index % 5 == 0 || index == total - 1) {
                val fraction = if (total > 0) (index + 1).toFloat() / total else 1f
                onProgress?.invoke(
                    "Importing Facebook Posts",
                    "${index + 1} of $total (${(fraction * 100).toInt()}%)",
                    fraction
                )
            }
        }

        return FacebookImportResult(
            entryCount = total,
            notebookName = notebookName,
            photoCount = photoCount
        )
    }

    /**
     * Determines a clean title for the Facebook entry.
     */
    private fun deriveTitle(rawTitle: String, postText: String): String {
        // If postText has a natural first line, use that
        if (postText.isNotBlank()) {
            val firstLine = postText.lines().firstOrNull()?.trim() ?: ""
            if (firstLine.length in 1..60) {
                return firstLine.removePrefix("#").trim()
            }
        }

        // Clean out generic boilerplate titles like "Scott Davis updated his status."
        val lower = rawTitle.lowercase()
        if (lower.contains("updated their status") ||
            lower.contains("updated his status") ||
            lower.contains("updated her status") ||
            lower.contains("added a new photo") ||
            lower.contains("added a photo")
        ) {
            return ""
        }

        return rawTitle
    }
}
