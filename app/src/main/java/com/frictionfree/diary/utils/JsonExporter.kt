package com.frictionfree.diary.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.ExportData
import com.frictionfree.diary.data.repository.DiaryRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class ImportOutcome {
    data class NativeSuccess(val entryCount: Int) : ImportOutcome()
    data class DayOneSuccess(val entryCount: Int, val notebookName: String, val photoCount: Int) : ImportOutcome()
    data class FacebookSuccess(val entryCount: Int, val notebookName: String, val photoCount: Int) : ImportOutcome()
    data class Error(val message: String) : ImportOutcome()
}

object JsonExporter {
    const val BACKUP_JSON_FILENAME = "frictionfree-backup.json"
    private const val MAX_ARCHIVE_ASSET_COUNT = 5000
    private const val MAX_ARCHIVE_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

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

    /**
     * Creates a portable ZIP package containing frictionfree-backup.json and media/ assets.
     */
    fun writeExportFile(context: Context, data: ExportData): File {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val filename = "frictionfree_diary_export_${System.currentTimeMillis()}.zip"
        val zipFile = File(exportDir, filename)

        // 1. Map each distinct local media URI across all entries to a media/<name> package path
        val distinctMediaPaths = data.entries.flatMap { it.mediaUris }.distinct()
        val mediaPathToPackagePath = mutableMapOf<String, String>()
        val usedPackageNames = mutableSetOf<String>()

        distinctMediaPaths.forEachIndexed { index, localPath ->
            val ext = getExtension(localPath).ifBlank { "jpg" }
            val baseName = "image_${index + 1}.$ext"
            var candidateName = baseName
            var counter = 2
            while (usedPackageNames.contains(candidateName)) {
                candidateName = "image_${index + 1}_$counter.$ext"
                counter++
            }
            usedPackageNames.add(candidateName)
            mediaPathToPackagePath[localPath] = "media/$candidateName"
        }

        // 2. Rewrite entries with the package-relative media paths
        val rewrittenEntries = data.entries.map { entry ->
            val rewrittenMedia = entry.mediaUris.map { originalPath ->
                mediaPathToPackagePath[originalPath] ?: originalPath
            }
            entry.copy(mediaUris = rewrittenMedia)
        }
        val packageData = data.copy(entries = rewrittenEntries)
        val jsonBytes = exportToJsonString(packageData).toByteArray(Charsets.UTF_8)

        // 3. Write ZIP stream containing frictionfree-backup.json and media/
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Write JSON
            val jsonZipEntry = ZipEntry(BACKUP_JSON_FILENAME)
            zos.putNextEntry(jsonZipEntry)
            zos.write(jsonBytes)
            zos.closeEntry()

            // Write media files
            for ((localPath, packagePath) in mediaPathToPackagePath) {
                val mediaInputStream = openMediaInputStream(context, localPath)
                    ?: throw IllegalStateException("Cannot read referenced image for backup: $localPath")

                mediaInputStream.use { input ->
                    val mediaZipEntry = ZipEntry(packagePath)
                    zos.putNextEntry(mediaZipEntry)
                    input.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }

        return zipFile
    }

    private fun getExtension(path: String): String {
        val clean = path.substringBefore('?').substringBefore('#')
        val lastSlash = clean.lastIndexOf('/')
        val lastBackslash = clean.lastIndexOf('\\')
        val fileName = clean.substring(maxOf(lastSlash, lastBackslash) + 1)
        val dot = fileName.lastIndexOf('.')
        return if (dot != -1 && dot < fileName.length - 1) {
            fileName.substring(dot + 1).lowercase()
        } else ""
    }

    private fun openMediaInputStream(context: Context, path: String): InputStream? {
        return try {
            when {
                path.startsWith("content://") || path.startsWith("file://") -> {
                    context.contentResolver.openInputStream(Uri.parse(path))
                }
                else -> {
                    val file = File(path)
                    if (file.exists() && file.isFile && file.canRead()) {
                        FileInputStream(file)
                    } else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unified multi-file importer supporting multi-part archives (e.g. split Facebook exports),
     * single archives, and batch file imports.
     */
    suspend fun importMultipleBackupsOrArchives(
        context: Context,
        uris: List<Uri>,
        diaryRepository: DiaryRepository,
        targetType: String? = null,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)? = null
    ): ImportOutcome {
        if (uris.isEmpty()) return ImportOutcome.Error("No files selected.")
        if (uris.size == 1) {
            return importBackupOrArchive(context, uris[0], diaryRepository, targetType, onProgress)
        }

        return try {
            val allZips = uris.all { isZipFile(context, it) }
            val isFb = targetType == "Facebook" || (allZips && uris.any { isFacebookZipArchive(context, it) })

            if (isFb) {
                val fbResult = FacebookImporter.importZips(context, uris, diaryRepository, onProgress)
                ImportOutcome.FacebookSuccess(
                    entryCount = fbResult.entryCount,
                    notebookName = fbResult.notebookName,
                    photoCount = fbResult.photoCount
                )
            } else {
                var totalEntries = 0
                for (uri in uris) {
                    val outcome = importBackupOrArchive(context, uri, diaryRepository, targetType, onProgress)
                    when (outcome) {
                        is ImportOutcome.NativeSuccess -> totalEntries += outcome.entryCount
                        is ImportOutcome.DayOneSuccess -> totalEntries += outcome.entryCount
                        is ImportOutcome.FacebookSuccess -> totalEntries += outcome.entryCount
                        is ImportOutcome.Error -> return outcome
                    }
                }
                ImportOutcome.NativeSuccess(entryCount = totalEntries)
            }
        } catch (e: Exception) {
            ImportOutcome.Error("Import failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Unified importer supporting native FrictionFree Diary JSON/ZIP backups,
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
                val isNative = targetType == "Native" || (targetType == null && isFrictionFreeZipArchive(context, uri))
                if (isNative) {
                    importNativeZip(context, uri, diaryRepository, onProgress)
                } else {
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

    /**
     * Imports a FrictionFree Diary portable ZIP package.
     */
    suspend fun importNativeZip(
        context: Context,
        uri: Uri,
        diaryRepository: DiaryRepository,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)? = null
    ): ImportOutcome {
        val tempZipFile = File(context.cacheDir, "native_import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.zip")
        val stagingDir = File(context.cacheDir, "staging_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}")
        val copiedFiles = mutableListOf<File>()

        try {
            onProgress?.invoke("Opening Package", "Reading backup archive...", null)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return ImportOutcome.Error("Unable to open selected archive.")

            var jsonContent: String? = null
            var assetCount = 0
            var uncompressedBytes = 0L
            val archiveEntryNames = mutableSetOf<String>()

            // 1. Validate ZIP entries & check constraints
            ZipFile(tempZipFile).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val normalizedName = entry.name.replace('\\', '/')

                    // Check path traversal and absolute paths
                    if (normalizedName.startsWith("/") || normalizedName.startsWith("\\") ||
                        normalizedName.split('/').any { it == ".." || it == "." && it.isNotEmpty() }
                    ) {
                        return ImportOutcome.Error("Malformed archive: unsafe entry path '$normalizedName'.")
                    }

                    if (archiveEntryNames.contains(normalizedName)) {
                        return ImportOutcome.Error("Malformed archive: duplicate entry '$normalizedName'.")
                    }
                    archiveEntryNames.add(normalizedName)

                    if (!entry.isDirectory) {
                        assetCount++
                        if (assetCount > MAX_ARCHIVE_ASSET_COUNT) {
                            return ImportOutcome.Error("Archive exceeds maximum allowed file count ($MAX_ARCHIVE_ASSET_COUNT).")
                        }

                        val size = if (entry.size >= 0) entry.size else 0L
                        uncompressedBytes += size
                        if (uncompressedBytes > MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                            return ImportOutcome.Error("Archive exceeds maximum uncompressed size limit (2 GB).")
                        }

                        if (normalizedName == BACKUP_JSON_FILENAME) {
                            zipFile.getInputStream(entry).use { inStream ->
                                jsonContent = inStream.bufferedReader(Charsets.UTF_8).readText()
                            }
                        }
                    }
                }
            }

            if (jsonContent.isNullOrBlank()) {
                return ImportOutcome.Error("Backup archive is missing $BACKUP_JSON_FILENAME.")
            }

            // 2. Parse and validate JSON
            val nativeData = try {
                parseFromJsonString(jsonContent!!)
            } catch (e: Exception) {
                return ImportOutcome.Error("Invalid $BACKUP_JSON_FILENAME: ${e.localizedMessage}")
            }

            // 3. Collect all referenced media paths in entries
            val referencedPackagePaths = mutableSetOf<String>()
            for (entry in nativeData.entries) {
                for (mediaPath in entry.mediaUris) {
                    val normPath = mediaPath.replace('\\', '/').trimStart('/')
                    if (normPath.startsWith("media/")) {
                        referencedPackagePaths.add(normPath)
                    }
                }
            }

            // Validate that all referenced media exist in archive
            for (refPath in referencedPackagePaths) {
                if (!archiveEntryNames.contains(refPath)) {
                    return ImportOutcome.Error("Archive is missing referenced image: $refPath")
                }
            }

            // 4. Extract referenced media to staging directory & validate image headers
            if (!stagingDir.exists()) stagingDir.mkdirs()
            val stagedFiles = mutableMapOf<String, File>() // packagePath -> staged file

            ZipFile(tempZipFile).use { zipFile ->
                for (refPath in referencedPackagePaths) {
                    val zipEntry = zipFile.getEntry(refPath)
                        ?: return ImportOutcome.Error("Referenced image entry not found in archive: $refPath")

                    val stagedFile = File(stagingDir, "stage_${UUID.randomUUID().toString().take(8)}_${File(refPath).name}")
                    zipFile.getInputStream(zipEntry).use { inStream ->
                        FileOutputStream(stagedFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    // Validate image content
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(stagedFile.absolutePath, opts)
                    if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                        return ImportOutcome.Error("Unreadable or invalid image file in archive: $refPath")
                    }
                    stagedFiles[refPath] = stagedFile
                }
            }

            // 5. Copy validated images from staging into app's private filesDir/media directory
            val mediaDir = File(context.filesDir, "media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            val packagePathToLocalPath = mutableMapOf<String, String>()
            for ((packagePath, stagedFile) in stagedFiles) {
                val ext = getExtension(packagePath).ifBlank { "jpg" }
                val targetFileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
                val destFile = File(mediaDir, targetFileName)

                stagedFile.inputStream().use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                copiedFiles.add(destFile)
                packagePathToLocalPath[packagePath] = destFile.absolutePath
            }

            // 6. Rewrite entry mediaUris to local paths
            val rewrittenEntries = nativeData.entries.map { entry ->
                val newMediaUris = entry.mediaUris.map { originalPath ->
                    val normPath = originalPath.replace('\\', '/').trimStart('/')
                    if (normPath.startsWith("media/") && packagePathToLocalPath.containsKey(normPath)) {
                        packagePathToLocalPath[normPath]!!
                    } else {
                        originalPath
                    }
                }
                entry.copy(mediaUris = newMediaUris)
            }

            // 7. Check existing entry IDs before import to track which entries are inserted vs skipped.
            // Note: importData processes entries sequentially with overwriteExisting = false,
            // so if multiple entries within the same payload share an ID, only the first occurrence is saved.
            val skippedEntryIds = mutableSetOf<String>()
            val processedInBatch = mutableSetOf<String>()
            val savedEntries = mutableListOf<DiaryEntry>()

            for (entry in rewrittenEntries) {
                val existsInDb = diaryRepository.getEntryByIdDirect(entry.id) != null
                if (existsInDb || processedInBatch.contains(entry.id)) {
                    skippedEntryIds.add(entry.id)
                } else {
                    savedEntries.add(entry)
                    processedInBatch.add(entry.id)
                }
            }

            val importDataPayload = nativeData.copy(entries = rewrittenEntries)
            diaryRepository.importData(importDataPayload, overwriteExisting = false, onProgress = onProgress)

            // 8. Cleanup copied files that are not referenced by newly saved entries
            val savedReferencedLocalPaths = savedEntries.flatMap { it.mediaUris }.toSet()

            for (copiedFile in copiedFiles) {
                if (!savedReferencedLocalPaths.contains(copiedFile.absolutePath)) {
                    copiedFile.delete()
                }
            }

            return ImportOutcome.NativeSuccess(entryCount = nativeData.entries.size)
        } catch (e: Exception) {
            // Clean up newly copied files on failure
            for (copiedFile in copiedFiles) {
                if (copiedFile.exists()) {
                    copiedFile.delete()
                }
            }
            return ImportOutcome.Error("Import failed: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            if (tempZipFile.exists()) tempZipFile.delete()
            if (stagingDir.exists()) stagingDir.deleteRecursively()
        }
    }

    fun isFrictionFreeZipArchive(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/').trimStart('/')
                        if (name == BACKUP_JSON_FILENAME) {
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

    fun isFacebookZipArchive(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zipStream ->
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

    fun isZipFile(context: Context, uri: Uri): Boolean {
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

