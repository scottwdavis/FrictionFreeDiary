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
}
