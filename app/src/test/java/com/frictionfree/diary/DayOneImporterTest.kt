package com.frictionfree.diary

import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.ExportData
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.model.Tag
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.utils.DayOneImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayOneImporterTest {

    @Test
    fun testParseTitleAndContentWithEscapedAmpersand() {
        val rawText = "# 1 \\& 2 Peter (Nov 20-26 2023)\n> What is it about the plan of salvation?"
        val (title, content) = DayOneImporter.parseTitleAndContent(rawText)

        assertEquals("1 & 2 Peter (Nov 20-26 2023)", title)
        assertEquals("> What is it about the plan of salvation?", content)
    }

    @Test
    fun testParseIsoDate() {
        val dateString = "2023-11-22T04:23:40Z"
        val epochMillis = DayOneImporter.parseIsoDate(dateString)

        assertNotNull(epochMillis)
        assertTrue(epochMillis!! > 1700000000000L) // in Nov 2023
    }

    @Test
    fun testDetectDayOneJson() {
        val json = """
            {
                "metadata": { "version": "1.0" },
                "entries": [
                    {
                        "uuid": "2D1AF680C78F4DDBBA03BCFDCAD37D66",
                        "creationDate": "2023-11-22T04:23:40Z",
                        "text": "# Test"
                    }
                ]
            }
        """.trimIndent()

        assertTrue(DayOneImporter.isDayOneJson(json))
    }

    @Test
    fun testImportJsonContent() = runBlocking {
        val json = """
            {
                "metadata": { "version": "1.0" },
                "entries": [
                    {
                        "uuid": "2D1AF680C78F4DDBBA03BCFDCAD37D66",
                        "creationDate": "2023-11-22T04:23:40Z",
                        "text": "# 1 \\& 2 Peter (Nov 20-26 2023)\n> Quotes here with #faith",
                        "tags": ["scriptures"],
                        "starred": true,
                        "isPinned": false,
                        "location": {
                            "placeName": "117 Mist Mountain Rise",
                            "localityName": "Okotoks",
                            "latitude": 50.728962,
                            "longitude": -113.9900152
                        }
                    }
                ]
            }
        """.trimIndent()

        val savedEntries = mutableListOf<DiaryEntry>()
        val savedNotebooks = mutableListOf<Notebook>()

        val fakeRepository = object : DiaryRepository {
            override fun getAllEntries(isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(savedEntries)
            override fun getEntryById(id: String): Flow<DiaryEntry?> = flowOf(savedEntries.firstOrNull { it.id == id })
            override suspend fun getEntryByIdDirect(id: String): DiaryEntry? = savedEntries.firstOrNull { it.id == id }
            override fun getEntriesByNotebook(notebookId: String, isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(emptyList())
            override fun getEntriesByTag(tagName: String, isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(emptyList())
            override fun searchEntries(query: String, isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(emptyList())
            override fun getEntriesInRange(startTime: Long, endTime: Long, isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(emptyList())

            override suspend fun saveEntry(entry: DiaryEntry): String {
                savedEntries.add(entry)
                return entry.id
            }

            override suspend fun deleteEntry(id: String) {}
            override suspend fun deleteEntries(entryIds: List<String>) {}
            override suspend fun archiveEntries(entryIds: List<String>, isArchived: Boolean) {}
            override suspend fun moveEntriesToNotebook(entryIds: List<String>, notebookId: String) {}
            override fun getAllNotebooks(): Flow<List<Notebook>> = flowOf(savedNotebooks)
            override suspend fun saveNotebook(notebook: Notebook) {
                savedNotebooks.add(notebook)
            }
            override suspend fun deleteNotebook(id: String) {}
            override suspend fun getNotebookById(id: String): Notebook? = savedNotebooks.firstOrNull { it.id == id }
            override suspend fun ensureDefaultNotebooks() {}
            override fun getAllTags(): Flow<List<Tag>> = flowOf(emptyList())
            override suspend fun getExportData(): ExportData = ExportData()
            override suspend fun importData(exportData: ExportData, overwriteExisting: Boolean) {}
        }

        val result = DayOneImporter.importJsonContent(
            jsonContent = json,
            notebookName = "Come Follow Me",
            photoPathMap = emptyMap(),
            diaryRepository = fakeRepository
        )

        assertEquals(1, result.entryCount)
        assertEquals("Come Follow Me", result.notebookName)
        assertEquals(1, savedNotebooks.size)
        assertEquals("Come Follow Me", savedNotebooks[0].name)
        assertEquals(1, savedEntries.size)

        val entry = savedEntries[0]
        assertEquals("2D1AF680C78F4DDBBA03BCFDCAD37D66", entry.id)
        assertEquals("1 & 2 Peter (Nov 20-26 2023)", entry.title)
        assertEquals("> Quotes here with #faith", entry.content)
        assertTrue(entry.isFavorite)
        assertEquals(50.728962, entry.latitude ?: 0.0, 0.0001)
        assertEquals(-113.9900152, entry.longitude ?: 0.0, 0.0001)
        assertEquals("117 Mist Mountain Rise, Okotoks", entry.locationName)
        assertTrue(entry.tags.contains("scriptures"))
    }

    @Test
    fun testRealDayOneZipIfPresent() = runBlocking {
        val file = java.io.File("""C:\Users\Scott\Downloads\09-06-2026_03-37-PM-Come Follow Me .zip""")
        if (!file.exists()) return@runBlocking

        val zipFile = java.util.zip.ZipFile(file)
        val jsonEntry = zipFile.entries().asSequence().firstOrNull { it.name.endsWith(".json", ignoreCase = true) }
        assertNotNull(jsonEntry)

        val content = zipFile.getInputStream(jsonEntry).bufferedReader().readText()
        zipFile.close()

        assertTrue(DayOneImporter.isDayOneJson(content))
        val derivedNotebook = jsonEntry!!.name.removeSuffix(".json").trim()
        assertEquals("Come Follow Me", derivedNotebook)

        val savedEntries = mutableListOf<DiaryEntry>()
        val savedNotebooks = mutableListOf<Notebook>()

        val fakeRepo = object : DiaryRepository {
            override fun getAllEntries(isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(savedEntries)
            override fun getEntryById(id: String): Flow<DiaryEntry?> = flowOf(savedEntries.firstOrNull { it.id == id })
            override suspend fun getEntryByIdDirect(id: String): DiaryEntry? = savedEntries.firstOrNull { it.id == id }
            override fun getEntriesByNotebook(notebookId: String, isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(emptyList())
            override fun getEntriesByTag(tagName: String, isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(emptyList())
            override fun searchEntries(query: String, isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(emptyList())
            override fun getEntriesInRange(startTime: Long, endTime: Long, isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(emptyList())
            override suspend fun saveEntry(entry: DiaryEntry): String {
                savedEntries.add(entry)
                return entry.id
            }
            override suspend fun deleteEntry(id: String) {}
            override suspend fun deleteEntries(entryIds: List<String>) {}
            override suspend fun archiveEntries(entryIds: List<String>, isArchived: Boolean) {}
            override suspend fun moveEntriesToNotebook(entryIds: List<String>, notebookId: String) {}
            override fun getAllNotebooks(): Flow<List<Notebook>> = flowOf(savedNotebooks)
            override suspend fun saveNotebook(notebook: Notebook) {
                savedNotebooks.add(notebook)
            }
            override suspend fun deleteNotebook(id: String) {}
            override suspend fun getNotebookById(id: String): Notebook? = savedNotebooks.firstOrNull { it.id == id }
            override suspend fun ensureDefaultNotebooks() {}
            override fun getAllTags(): Flow<List<Tag>> = flowOf(emptyList())
            override suspend fun getExportData(): ExportData = ExportData()
            override suspend fun importData(exportData: ExportData, overwriteExisting: Boolean) {}
        }

        val result = DayOneImporter.importJsonContent(content, derivedNotebook, emptyMap(), fakeRepo)
        assertEquals(15, result.entryCount)
        assertEquals("Come Follow Me", result.notebookName)
        assertEquals(15, savedEntries.size)

        // Verify dates, titles, and locations across parsed entries
        val first = savedEntries[0]
        assertEquals("1 & 2 Peter (Nov 20-26 2023)", first.title)
        assertNotNull(first.latitude)
        assertEquals("117 Mist Mountain Rise, Okotoks", first.locationName)
    }
}
