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

    @Test
    fun testMemoryPostCaptionExtraction() = runBlocking {
        // Facebook Memory post: no post body in data, caption stored in attachment text & media description
        val json = """
            [
              {
                "timestamp": 1725381778,
                "title": "Scott Davis shared a memory.",
                "attachments": [
                  {
                    "data": [
                      {
                        "text": "8 Years Ago"
                      },
                      {
                        "text": "Scott Davis added a new photo."
                      },
                      {
                        "text": "Sep 03, 2016 9:52:45 pm"
                      },
                      {
                        "text": "So at Wedgemount Lake, near Whistler, there's a hut at the top of the hike that Lorelei and I slept in."
                      },
                      {
                        "media": {
                          "uri": "your_facebook_activity/posts/media/Photos_52908282036/10154485910222037.jpg",
                          "description": "So at Wedgemount Lake, near Whistler, there's a hut at the top of the hike that Lorelei and I slept in."
                        }
                      }
                    ]
                  }
                ],
                "data": [
                  {
                    "update_timestamp": 1725381778
                  }
                ]
              }
            ]
        """.trimIndent()

        val savedEntries = mutableListOf<DiaryEntry>()
        val fakeRepo = object : DiaryRepository {
            override fun getAllEntries(isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(savedEntries)
            override fun getEntryById(id: String): Flow<DiaryEntry?> = flowOf(null)
            override suspend fun getEntryByIdDirect(id: String): DiaryEntry? = null
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
            override fun getAllNotebooks(): Flow<List<Notebook>> = flowOf(emptyList())
            override fun getNotebookEntryCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
            override suspend fun saveNotebook(notebook: Notebook) {}
            override suspend fun deleteNotebook(id: String, deleteEntries: Boolean, targetNotebookId: String?) {}
            override suspend fun getNotebookById(id: String): Notebook? = null
            override suspend fun ensureDefaultNotebooks() {}
            override fun getAllTags(): Flow<List<Tag>> = flowOf(emptyList())
            override suspend fun getExportData(): ExportData = ExportData()
            override suspend fun importData(exportData: ExportData, overwriteExisting: Boolean, onProgress: ((title: String, detail: String, progress: Float?) -> Unit)?) {}
        }

        val photoMap = mapOf("10154485910222037.jpg" to "/data/media/wedgemount.jpg")

        val result = FacebookImporter.importJsonContent(
            jsonContent = json,
            notebookName = "Facebook",
            photoPathMap = photoMap,
            diaryRepository = fakeRepo
        )

        assertEquals(1, result.entryCount)
        assertEquals(1, result.photoCount)
        assertEquals(1, savedEntries.size)

        val entry = savedEntries[0]
        // Memory post text should be extracted as a clean blockquote
        assertTrue(entry.content.contains("Wedgemount Lake"))
        assertTrue(entry.content.contains("> **Shared Memory (Sep 03, 2016 • 8 Years Ago):**"))
        // Derived title should be derived from the first line of the memory caption
        assertTrue(entry.title.contains("Wedgemount Lake"))
        // Photo from map attached
        assertEquals(listOf("/data/media/wedgemount.jpg"), entry.mediaUris)
    }

    @Test
    fun testRepostWithCommentExtraction() = runBlocking {
        // Facebook Repost of memory with additional comment
        val json = """
            [
              {
                "timestamp": 1725134254,
                "title": "Scott Davis shared a memory.",
                "data": [
                  {
                    "post": "If me from 5 years ago could see what I am doing now, his mind would be blown."
                  }
                ],
                "attachments": [
                  {
                    "data": [
                      {
                        "text": "5 Years Ago"
                      },
                      {
                        "text": "Scott Davis updated his status."
                      },
                      {
                        "text": "Aug 31, 2019 4:37:34 pm"
                      },
                      {
                        "text": "Things I have learned how to do in the last 18 months:\n- built a custom CRM\n- designed an automation engine"
                      }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val savedEntries = mutableListOf<DiaryEntry>()
        val fakeRepo = object : DiaryRepository {
            override fun getAllEntries(isArchived: Boolean): Flow<List<DiaryEntry>> = flowOf(savedEntries)
            override fun getEntryById(id: String): Flow<DiaryEntry?> = flowOf(null)
            override suspend fun getEntryByIdDirect(id: String): DiaryEntry? = null
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
            override fun getAllNotebooks(): Flow<List<Notebook>> = flowOf(emptyList())
            override fun getNotebookEntryCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
            override suspend fun saveNotebook(notebook: Notebook) {}
            override suspend fun deleteNotebook(id: String, deleteEntries: Boolean, targetNotebookId: String?) {}
            override suspend fun getNotebookById(id: String): Notebook? = null
            override suspend fun ensureDefaultNotebooks() {}
            override fun getAllTags(): Flow<List<Tag>> = flowOf(emptyList())
            override suspend fun getExportData(): ExportData = ExportData()
            override suspend fun importData(exportData: ExportData, overwriteExisting: Boolean, onProgress: ((title: String, detail: String, progress: Float?) -> Unit)?) {}
        }

        val result = FacebookImporter.importJsonContent(
            jsonContent = json,
            notebookName = "Facebook",
            photoPathMap = emptyMap(),
            diaryRepository = fakeRepo
        )

        assertEquals(1, result.entryCount)
        assertEquals(1, savedEntries.size)

        val entry = savedEntries[0]
        // User's own comment should be at the top
        assertTrue(entry.content.startsWith("If me from 5 years ago could see"))
        // Quoted block with date and relative time should be appended
        assertTrue(entry.content.contains("> **Shared Memory (Aug 31, 2019 • 5 Years Ago):**"))
        assertTrue(entry.content.contains("> Things I have learned how to do"))
        // Title should be derived from user's comment
        assertTrue(entry.title.contains("If me from 5 years ago"))
    }

    @Test
    fun testIsPostJsonFile() {
        // True for legitimate post files
        assertTrue(FacebookImporter.isPostJsonFile("your_posts_1.json", "your_facebook_activity/posts/your_posts_1.json"))
        assertTrue(FacebookImporter.isPostJsonFile("your_posts__check_ins__photos_and_videos_1.json", "your_facebook_activity/posts/your_posts__check_ins__photos_and_videos_1.json"))
        assertTrue(FacebookImporter.isPostJsonFile("posts.json", "posts.json"))
        assertTrue(FacebookImporter.isPostJsonFile("status_updates.json", "posts/status_updates.json"))

        // False for auxiliary metadata files that caused the 820 empty entries bug
        assertTrue(!FacebookImporter.isPostJsonFile("edits_you_made_to_posts.json", "your_facebook_activity/posts/edits_you_made_to_posts.json"))
        assertTrue(!FacebookImporter.isPostJsonFile("places_you_have_been_tagged_in.json", "your_facebook_activity/posts/places_you_have_been_tagged_in.json"))
        assertTrue(!FacebookImporter.isPostJsonFile("0.json", "your_facebook_activity/posts/album/0.json"))
        assertTrue(!FacebookImporter.isPostJsonFile("36.json", "your_facebook_activity/posts/album/36.json"))
        assertTrue(!FacebookImporter.isPostJsonFile("media_used_for_memories.json", "your_facebook_activity/posts/media_used_for_memories.json"))
        assertTrue(!FacebookImporter.isPostJsonFile("content_sharing_links_you_have_created.json", "your_facebook_activity/posts/content_sharing_links_you_have_created.json"))
        assertTrue(!FacebookImporter.isPostJsonFile("items_sold.json", "your_facebook_activity/posts/items_sold.json"))
        assertTrue(!FacebookImporter.isPostJsonFile("uncategorized_photos.json", "your_facebook_activity/posts/your_uncategorized_photos.json"))
    }

    @Test
    fun testObjectTagsParsing() {
        val json = """
            [
              {
                "timestamp": 1784953509,
                "title": "Scott Davis added 5 new photos.",
                "data": [
                  {
                    "post": "Come hang out and talk 3D printing with me."
                  }
                ],
                "tags": [
                  {
                    "name": "Samantha Davis"
                  }
                ]
              }
            ]
        """.trimIndent()

        val posts = FacebookImporter.parsePostsJson(json)
        assertEquals(1, posts.size)
        assertEquals(1, posts[0].tags.size)
        assertEquals("Samantha Davis", posts[0].tags[0].name)
    }
}
