package com.frictionfree.diary.utils

import android.content.Context
import android.net.Uri
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.EntryColor
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.repository.DiaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Serializable
data class FacebookTag(
    val name: String? = null
)

@Serializable
data class FacebookPost(
    val timestamp: Long? = null,
    val title: String? = null,
    val data: List<FacebookPostData> = emptyList(),
    val attachments: List<FacebookAttachment> = emptyList(),
    val tags: List<FacebookTag> = emptyList()
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
     * Identifies legitimate Facebook user post export files while filtering out
     * auxiliary metadata (albums, revision edits, tag lists, sales, administrative files).
     */
    fun isPostJsonFile(fileName: String, entryName: String): Boolean {
        val lowerFile = fileName.lowercase()
        val lowerEntry = entryName.replace('\\', '/').lowercase()
        if (!lowerFile.endsWith(".json")) return false

        // Exclude non-post auxiliary files, albums, edit histories, tag metadata, sales, and administrative files
        if (lowerEntry.contains("/album/") || lowerFile.startsWith("album")) return false
        if (lowerFile.contains("edits_you_made")) return false
        if (lowerFile.contains("places_you_have_been_tagged")) return false
        if (lowerFile.contains("media_used_for_memories")) return false
        if (lowerFile.contains("content_sharing_links")) return false
        if (lowerFile.contains("items_sold")) return false
        if (lowerFile.contains("uncategorized_photos")) return false
        if (lowerFile.contains("your_videos")) return false
        if (lowerFile.contains("check-ins")) return false

        // Primary post archives: your_posts_*.json, your_posts__*.json, posts.json, status_updates.json
        return lowerFile.startsWith("your_posts") ||
                lowerFile == "posts.json" ||
                lowerFile.startsWith("posts_") ||
                lowerFile == "status_updates.json"
    }

    /**
     * Imports from a single Facebook ZIP archive.
     */
    suspend fun importZip(
        context: Context,
        zipUri: Uri,
        diaryRepository: DiaryRepository,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)? = null
    ): FacebookImportResult {
        return importZips(context, listOf(zipUri), diaryRepository, onProgress)
    }

    /**
     * Imports from one or multiple Facebook ZIP archives.
     * When Meta splits large exports into multiple ZIP parts (e.g. JSON in part 1, overflow photos in part 2),
     * this aggregates media and posts across all archives before inserting into the diary repository.
     */
    suspend fun importZips(
        context: Context,
        zipUris: List<Uri>,
        diaryRepository: DiaryRepository,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)? = null
    ): FacebookImportResult = withContext(Dispatchers.IO) {
        val totalArchives = zipUris.size
        val mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) mediaDir.mkdirs()

        val jsonContents = mutableListOf<String>()
        val photoPathMap = mutableMapOf<String, String>() // normalized relative uri / filename -> absolute file path

        zipUris.forEachIndexed { archiveIndex, uri ->
            val partNum = archiveIndex + 1
            val prefix = if (totalArchives > 1) "Part $partNum of $totalArchives: " else ""
            onProgress?.invoke(
                "Extracting Facebook Archive",
                "${prefix}Extracting photos & posts...",
                (archiveIndex.toFloat() / totalArchives.coerceAtLeast(1))
            )

            val tempZipFile = File(context.cacheDir, "fb_temp_${System.currentTimeMillis()}_$archiveIndex.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempZipFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@forEachIndexed

                java.util.zip.ZipFile(tempZipFile).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        val fileName = File(entryName).name.lowercase()

                        if (!entry.isDirectory) {
                            if (isPostJsonFile(fileName, entryName)) {
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
        }

        if (jsonContents.isEmpty()) {
            throw IllegalArgumentException("No valid Facebook post JSON files found across the selected archive(s).")
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

        // Clean up any empty/malformed Facebook posts previously imported from auxiliary metadata
        try {
            val existingEntries = diaryRepository.getAllEntries(isArchived = false).firstOrNull() ?: emptyList()
            val badEntries = existingEntries.filter {
                it.notebookId == notebookId && (it.content.isBlank() || it.content == "Facebook Post" || (it.title == "Facebook Post" && it.mediaUris.isEmpty()))
            }
            if (badEntries.isNotEmpty()) {
                diaryRepository.deleteEntries(badEntries.map { it.id })
            }
        } catch (_: Exception) {}

        var photoCount = 0
        var savedCount = 0
        val total = posts.size

        // 2. Map and save entries
        posts.forEachIndexed { index, post ->
            val rawPostText = post.data.mapNotNull { it.post }.firstOrNull() ?: ""
            var cleanPostText = repairFacebookEncoding(rawPostText).trim()
            val rawTitle = repairFacebookEncoding(post.title).trim()

            // Resolve photos from attachments
            val mediaUris = mutableListOf<String>()
            val externalLinks = mutableListOf<String>()
            val attachmentCaptions = mutableListOf<String>()

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
                    item.media?.description?.let { desc ->
                        val cleanDesc = repairFacebookEncoding(desc).trim()
                        if (cleanDesc.isNotBlank() && !attachmentCaptions.contains(cleanDesc)) {
                            attachmentCaptions.add(cleanDesc)
                        }
                    }
                    item.text?.let { txt ->
                        val cleanTxt = repairFacebookEncoding(txt).trim()
                        if (isValuableCaption(cleanTxt) && !attachmentCaptions.contains(cleanTxt)) {
                            attachmentCaptions.add(cleanTxt)
                        }
                    }
                    item.external_context?.url?.let { linkUrl ->
                        val linkName = repairFacebookEncoding(item.external_context.name).ifBlank { linkUrl }
                        externalLinks.add("[$linkName]($linkUrl)")
                    }
                }
            }

            // If root post body was blank, use captions discovered in attachments/media
            if (cleanPostText.isBlank() && attachmentCaptions.isNotEmpty()) {
                cleanPostText = attachmentCaptions.joinToString("\n\n")
            } else if (cleanPostText.isNotBlank() && attachmentCaptions.isNotEmpty()) {
                val extraCaptions = attachmentCaptions.filter { !cleanPostText.contains(it) }
                if (extraCaptions.isNotEmpty()) {
                    cleanPostText += "\n\n" + extraCaptions.joinToString("\n\n")
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
                if (mediaUris.isNotEmpty()) "Shared photos" else rawTitle.ifBlank { "" }
            }

            // Skip post if there's no content and no photos
            if (finalContent.isBlank() && mediaUris.isEmpty()) {
                return@forEachIndexed
            }

            // Timestamps: Facebook exports in Unix epoch seconds. Skip if absent.
            val epochSeconds = post.timestamp ?: return@forEachIndexed
            val epochMillis = epochSeconds * 1000L

            // Title derivation: clean out generic "X updated his status" boilerplate
            val derivedTitle = deriveTitle(rawTitle, cleanPostText).ifBlank {
                if (cleanPostText.isNotBlank()) cleanPostText.take(40).trim()
                else if (mediaUris.isNotEmpty()) "Shared Photos"
                else "Facebook Post"
            }

            // Deterministic ID based on timestamp and content hash to prevent duplicates upon re-import
            val contentHash = (finalContent.take(30).hashCode() and 0xffff).toString(16)
            val entryId = "fb_${epochSeconds}_${contentHash}"

            val entry = DiaryEntry(
                id = entryId,
                title = derivedTitle,
                content = finalContent,
                notebookId = notebookId,
                colorHex = EntryColor.DEFAULT.hex,
                createdAt = epochMillis,
                updatedAt = epochMillis,
                mediaUris = mediaUris,
                tags = post.tags.mapNotNull { it.name }.map { repairFacebookEncoding(it) },
                isPinned = false,
                isFavorite = false
            )

            diaryRepository.saveEntry(entry)
            savedCount++

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
            entryCount = savedCount,
            notebookName = notebookName,
            photoCount = photoCount
        )
    }

    private val boilerplateRegex = Regex("""(?i)^\d+\s+years?\s+ago.*$""")
    private val dateRegex = Regex("""(?i)^[a-z]{3}\s+\d{1,2},\s+\d{4}.*$""")

    /**
     * Filters out Facebook system strings (like "8 Years Ago", dates, and sharing headers)
     * from attachment caption text so real user reflections are captured.
     */
    private fun isValuableCaption(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 3) return false
        if (trimmed.matches(boilerplateRegex)) return false
        if (trimmed.matches(dateRegex)) return false
        val lower = trimmed.lowercase()
        if (lower.endsWith("added a new photo.") ||
            lower.endsWith("added a photo.") ||
            lower.contains("added new photos") ||
            lower.contains("shared a memory") ||
            lower.contains("shared from instagram") ||
            lower.contains("updated his status") ||
            lower.contains("updated her status") ||
            lower.contains("updated their status")
        ) return false
        return true
    }

    /**
     * Determines a clean title for the Facebook entry.
     */
    private fun deriveTitle(rawTitle: String, postText: String): String {
        // If postText has a natural first line, use that
        if (postText.isNotBlank()) {
            val firstLine = postText.lines().firstOrNull()?.trim() ?: ""
            if (firstLine.isNotBlank()) {
                val clean = firstLine.removePrefix("#").trim()
                return if (clean.length <= 60) clean else clean.take(57).trimEnd() + "..."
            }
        }

        // Clean out generic boilerplate titles like "Scott Davis updated his status."
        val lower = rawTitle.lowercase()
        if (lower.contains("updated their status") ||
            lower.contains("updated his status") ||
            lower.contains("updated her status") ||
            lower.contains("added a new photo") ||
            lower.contains("added a photo") ||
            lower.contains("shared a memory")
        ) {
            return ""
        }

        return rawTitle
    }
}
