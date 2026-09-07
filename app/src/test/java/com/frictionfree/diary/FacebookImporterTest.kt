package com.frictionfree.diary

import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.ExportData
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.model.Tag
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.utils.FacebookImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookImporterTest {

    @Test
    fun testRepairFacebookEncoding() {
        // Facebook's Latin-1 / UTF-8 mojibake bug
        // '\u00e2\u0080\u0099' is the byte sequence for '’'
        val rawQuote = "It\u00e2\u0080\u0099s a sunny day!"
        val repairedQuote = FacebookImporter.repairFacebookEncoding(rawQuote)
        assertEquals("It’s a sunny day!", repairedQuote)

        // '\u00f0\u009f\u0098\u008a' is the byte sequence for '😊'
        val rawEmoji = "Great memory \u00f0\u009f\u0098\u008a"
        val repairedEmoji = FacebookImporter.repairFacebookEncoding(rawEmoji)
        assertEquals("Great memory 😊", repairedEmoji)

        // Standard ASCII remains completely intact
        val ascii = "Just a regular English sentence."
        assertEquals(ascii, FacebookImporter.repairFacebookEncoding(ascii))
    }

    @Test
    fun testIsFacebookJson() {
        val samplePost = """
            [
              {
                "timestamp": 1609459200,
                "data": [
                  {
                    "post": "Happy New Year 2021!"
                  }
                ]
              }
            ]
        """.trimIndent()

        assertTrue(FacebookImporter.isFacebookJson(samplePost))
    }

    @Test
    fun testParsePostsJson() {
        val json = """
            [
              {
                "timestamp": 1698765432,
                "title": "Scott Davis updated his status.",
                "data": [
                  {
                    "post": "Hiking in the Rockies today! #mountains #travel"
                  }
                ],
                "attachments": [
                  {
                    "data": [
                      {
                        "media": {
                          "uri": "posts/media/rockies.jpg",
                          "creation_timestamp": 1698765400,
                          "title": "Summit View"
                        },
                        "external_context": {
                          "url": "https://nationalparks.org",
                          "name": "National Parks Guide"
                        }
                      }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val posts = FacebookImporter.parsePostsJson(json)
        assertEquals(1, posts.size)

        val post = posts[0]
        assertEquals(1698765432L, post.timestamp)
        assertEquals("Hiking in the Rockies today! #mountains #travel", post.data[0].post)
        assertEquals(1, post.attachments.size)
        assertEquals("posts/media/rockies.jpg", post.attachments[0].data[0].media?.uri)
        assertEquals("https://nationalparks.org", post.attachments[0].data[0].external_context?.url)
    }

    @Test
    fun testImportJsonContent() = runBlocking {
        val json = """
            [
              {
                "timestamp": 1600000000,
                "title": "Scott Davis added a photo.",
                "data": [
                  {
                    "post": "Backyard barbecue with friends! \u00f0\u009f\u008d\u00bb #bbq #summer"
                  }
                ],
                "attachments": [
                  {
                    "data": [
                      {
                        "media": {
                          "uri": "your_facebook_activity/posts/media/your_posts/bbq_photo.jpg"
                        }
                      }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

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
            override fun getNotebookEntryCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
            override suspend fun saveNotebook(notebook: Notebook) {
                savedNotebooks.add(notebook)
            }
            override suspend fun deleteNotebook(id: String, deleteEntries: Boolean, targetNotebookId: String?) {}
            override suspend fun getNotebookById(id: String): Notebook? = savedNotebooks.firstOrNull { it.id == id }
            override suspend fun ensureDefaultNotebooks() {}
            override fun getAllTags(): Flow<List<Tag>> = flowOf(emptyList())
            override suspend fun getExportData(): ExportData = ExportData()
            override suspend fun importData(exportData: ExportData, overwriteExisting: Boolean, onProgress: ((title: String, detail: String, progress: Float?) -> Unit)?) {}
        }

        val photoMap = mapOf("your_facebook_activity/posts/media/your_posts/bbq_photo.jpg" to "/data/media/bbq_photo.jpg")

        val result = FacebookImporter.importJsonContent(
            jsonContent = json,
            notebookName = "Facebook",
            photoPathMap = photoMap,
            diaryRepository = fakeRepo
        )

        assertEquals(1, result.entryCount)
        assertEquals("Facebook", result.notebookName)
        assertEquals(1, result.photoCount)
        assertEquals(1, savedNotebooks.size)
        assertEquals("Facebook", savedNotebooks[0].name)
        assertEquals("#1877F2", savedNotebooks[0].colorHex)

        assertEquals(1, savedEntries.size)
        val entry = savedEntries[0]
        assertEquals("nb_facebook", entry.notebookId)
        // Timestamp should be converted from seconds (1600000000) to milliseconds (1600000000000)
        assertEquals(1600000000000L, entry.createdAt)
        // Mojibake beer emoji should be repaired
        assertTrue(entry.content.contains("🍻"))
        assertTrue(entry.content.contains("Backyard barbecue with friends!"))
        // Media attached
        assertEquals(1, entry.mediaUris.size)
        assertEquals("/data/media/bbq_photo.jpg", entry.mediaUris[0])
    }
}
