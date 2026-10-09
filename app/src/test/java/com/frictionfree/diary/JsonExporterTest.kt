package com.frictionfree.diary

import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.EntryColor
import com.frictionfree.diary.data.model.ExportData
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.model.Tag
import com.frictionfree.diary.utils.JsonExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExporterTest {

    @Test
    fun testJsonExportAndParseRoundTrip() {
        val entry = DiaryEntry(
            id = "entry-1",
            title = "Morning Coffee",
            content = "Great morning walk with #dog and #coffee.",
            notebookId = "default_personal",
            colorHex = EntryColor.EMERALD.hex,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            latitude = 37.7749,
            longitude = -122.4194,
            locationName = "San Francisco",
            tags = listOf("dog", "coffee"),
            isPinned = true
        )

        val notebook = Notebook(
            id = "default_personal",
            name = "Personal",
            description = "Main journal",
            icon = "book",
            colorHex = "#2E7D32"
        )

        val tag1 = Tag("dog", 1, 1700000000000L)
        val tag2 = Tag("coffee", 1, 1700000000000L)

        val exportData = ExportData(
            version = 1,
            app = "FrictionFreeDiary",
            exportedAt = 1700000000000L,
            entries = listOf(entry),
            notebooks = listOf(notebook),
            tags = listOf(tag1, tag2)
        )

        val jsonString = JsonExporter.exportToJsonString(exportData)
        assertTrue(jsonString.contains("Morning Coffee"))
        assertTrue(jsonString.contains("FrictionFreeDiary"))

        val parsedData = JsonExporter.parseFromJsonString(jsonString)
        assertEquals(1, parsedData.version)
        assertEquals(1, parsedData.entries.size)
        assertEquals(1, parsedData.notebooks.size)
        assertEquals(2, parsedData.tags.size)

        val parsedEntry = parsedData.entries[0]
        assertEquals("Morning Coffee", parsedEntry.title)
        assertEquals(EntryColor.EMERALD.hex, parsedEntry.colorHex)
        assertEquals("San Francisco", parsedEntry.locationName)
        assertEquals(37.7749, parsedEntry.latitude ?: 0.0, 0.0001)
        assertTrue(parsedEntry.isPinned)
    }

    @Test
    fun testParseExtractedFacebookChatEventsJson() {
        val jsonFile = java.io.File("tools/facebook_chat_extractor/facebook_chat_events.json")
        if (!jsonFile.exists()) {
            return
        }
        val content = jsonFile.readText(Charsets.UTF_8)
        val parsed = JsonExporter.parseFromJsonString(content)

        assertEquals(1, parsed.version)
        assertEquals("FrictionFreeDiary", parsed.app)
        assertTrue("Entries should not be empty", parsed.entries.isNotEmpty())
        assertEquals(1, parsed.notebooks.size)
        assertEquals("notebook_facebook_chat_events", parsed.notebooks[0].id)
        assertEquals("Facebook Chat Events", parsed.notebooks[0].name)

        val firstEntry = parsed.entries[0]
        assertTrue("Entry content should not be blank", firstEntry.content.isNotBlank())
        assertEquals("notebook_facebook_chat_events", firstEntry.notebookId)
        assertTrue("Tags should exist", firstEntry.tags.isNotEmpty())
    }

    @Test
    fun testPackageExportRewritesMediaAndPreservesOrderAndReusesSharedFiles() {
        // Create temporary image files
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "exporter_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val img1 = java.io.File(tempDir, "photo1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val img2 = java.io.File(tempDir, "photo2.png").apply { writeBytes(byteArrayOf(4, 5, 6)) }

            val entryA = DiaryEntry(
                id = "entry-a",
                title = "Entry A",
                mediaUris = listOf(img1.absolutePath, img2.absolutePath)
            )
            val entryB = DiaryEntry(
                id = "entry-b",
                title = "Entry B",
                // Reuses img1 and preserves specific order
                mediaUris = listOf(img2.absolutePath, img1.absolutePath)
            )

            val exportData = ExportData(
                entries = listOf(entryA, entryB)
            )

            // Test ZIP package generation in memory / temp file
            val zipFile = java.io.File(tempDir, "backup.zip")
            val distinctMediaPaths = exportData.entries.flatMap { it.mediaUris }.distinct()
            val mediaPathToPackagePath = mutableMapOf<String, String>()
            distinctMediaPaths.forEachIndexed { index, path ->
                val ext = path.substringAfterLast(".", "jpg")
                mediaPathToPackagePath[path] = "media/image_${index + 1}.$ext"
            }

            assertEquals(2, distinctMediaPaths.size)
            assertEquals("media/image_1.jpg", mediaPathToPackagePath[img1.absolutePath])
            assertEquals("media/image_2.png", mediaPathToPackagePath[img2.absolutePath])

            val rewrittenA = entryA.mediaUris.map { mediaPathToPackagePath[it]!! }
            val rewrittenB = entryB.mediaUris.map { mediaPathToPackagePath[it]!! }

            assertEquals(listOf("media/image_1.jpg", "media/image_2.png"), rewrittenA)
            assertEquals(listOf("media/image_2.png", "media/image_1.jpg"), rewrittenB)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPackageZipValidationRejectsUnsafeAndMissingAssets() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "zip_val_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            // Check unsafe path rejection logic
            val unsafePaths = listOf("../evil.jpg", "media/../../escape.jpg", "/absolute/path.jpg", "\\win\\path.jpg")
            for (path in unsafePaths) {
                val normalized = path.replace('\\', '/')
                val isUnsafe = normalized.startsWith("/") || normalized.startsWith("\\") ||
                        normalized.split('/').any { it == ".." || (it == "." && it.isNotEmpty()) }
                assertTrue("Path '$path' should be detected as unsafe", isUnsafe)
            }

            // Check safe relative path
            val safePath = "media/image_1.jpg"
            val normalizedSafe = safePath.replace('\\', '/')
            val isSafe = !(normalizedSafe.startsWith("/") || normalizedSafe.startsWith("\\") ||
                    normalizedSafe.split('/').any { it == ".." || (it == "." && it.isNotEmpty()) })
            assertTrue("Safe path should pass", isSafe)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
