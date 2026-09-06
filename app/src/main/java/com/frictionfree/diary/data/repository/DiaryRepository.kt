package com.frictionfree.diary.data.repository

import com.frictionfree.diary.data.local.dao.EntryDao
import com.frictionfree.diary.data.local.dao.NotebookDao
import com.frictionfree.diary.data.local.dao.TagDao
import com.frictionfree.diary.data.local.entities.DiaryEntryEntity
import com.frictionfree.diary.data.local.entities.EntryTagCrossRef
import com.frictionfree.diary.data.local.entities.NotebookEntity
import com.frictionfree.diary.data.local.entities.TagEntity
import com.frictionfree.diary.data.model.DiaryEntry
import com.frictionfree.diary.data.model.ExportData
import com.frictionfree.diary.data.model.Notebook
import com.frictionfree.diary.data.model.Tag
import com.frictionfree.diary.utils.HashtagParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

interface DiaryRepository {
    fun getAllEntries(): Flow<List<DiaryEntry>>
    fun getEntryById(id: String): Flow<DiaryEntry?>
    suspend fun getEntryByIdDirect(id: String): DiaryEntry?
    fun getEntriesByNotebook(notebookId: String): Flow<List<DiaryEntry>>
    fun getEntriesByTag(tagName: String): Flow<List<DiaryEntry>>
    fun searchEntries(query: String): Flow<List<DiaryEntry>>
    fun getEntriesInRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>>
    suspend fun saveEntry(entry: DiaryEntry): String
    suspend fun deleteEntry(id: String)

    // Notebooks
    fun getAllNotebooks(): Flow<List<Notebook>>
    suspend fun saveNotebook(notebook: Notebook)
    suspend fun deleteNotebook(id: String)
    suspend fun getNotebookById(id: String): Notebook?
    suspend fun ensureDefaultNotebooks()

    // Tags
    fun getAllTags(): Flow<List<Tag>>

    // Export / Import
    suspend fun getExportData(): ExportData
    suspend fun importData(exportData: ExportData, overwriteExisting: Boolean = false)
}

class DiaryRepositoryImpl(
    private val entryDao: EntryDao,
    private val notebookDao: NotebookDao,
    private val tagDao: TagDao
) : DiaryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getAllEntries(): Flow<List<DiaryEntry>> {
        return entryDao.getAllEntriesFlow().map { entities ->
            entities.map { entity ->
                val tags = tagDao.getTagsForEntry(entity.id)
                val media = decodeMediaList(entity.mediaUrisJson)
                entity.toDomain(tags, media)
            }
        }
    }

    override fun getEntryById(id: String): Flow<DiaryEntry?> {
        return entryDao.getEntryByIdFlow(id).map { entity ->
            if (entity == null) null
            else {
                val tags = tagDao.getTagsForEntry(entity.id)
                val media = decodeMediaList(entity.mediaUrisJson)
                entity.toDomain(tags, media)
            }
        }
    }

    override suspend fun getEntryByIdDirect(id: String): DiaryEntry? {
        val entity = entryDao.getEntryById(id) ?: return null
        val tags = tagDao.getTagsForEntry(entity.id)
        val media = decodeMediaList(entity.mediaUrisJson)
        return entity.toDomain(tags, media)
    }

    override fun getEntriesByNotebook(notebookId: String): Flow<List<DiaryEntry>> {
        return entryDao.getEntriesByNotebookFlow(notebookId).map { entities ->
            entities.map { entity ->
                val tags = tagDao.getTagsForEntry(entity.id)
                val media = decodeMediaList(entity.mediaUrisJson)
                entity.toDomain(tags, media)
            }
        }
    }

    override fun getEntriesByTag(tagName: String): Flow<List<DiaryEntry>> {
        return entryDao.getEntriesByTagFlow(tagName.lowercase()).map { entities ->
            entities.map { entity ->
                val tags = tagDao.getTagsForEntry(entity.id)
                val media = decodeMediaList(entity.mediaUrisJson)
                entity.toDomain(tags, media)
            }
        }
    }

    override fun searchEntries(query: String): Flow<List<DiaryEntry>> {
        if (query.isBlank()) return getAllEntries()
        return entryDao.searchEntriesFlow(query.trim()).map { entities ->
            entities.map { entity ->
                val tags = tagDao.getTagsForEntry(entity.id)
                val media = decodeMediaList(entity.mediaUrisJson)
                entity.toDomain(tags, media)
            }
        }
    }

    override fun getEntriesInRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>> {
        return entryDao.getEntriesInRangeFlow(startTime, endTime).map { entities ->
            entities.map { entity ->
                val tags = tagDao.getTagsForEntry(entity.id)
                val media = decodeMediaList(entity.mediaUrisJson)
                entity.toDomain(tags, media)
            }
        }
    }

    override suspend fun saveEntry(entry: DiaryEntry): String {
        val entryId = if (entry.id.isBlank()) UUID.randomUUID().toString() else entry.id
        val now = System.currentTimeMillis()
        val createdAt = if (entry.createdAt <= 0) now else entry.createdAt

        // 1. Parse hashtags from content and title
        val parsedTags = (HashtagParser.extractHashtags(entry.content) +
                HashtagParser.extractHashtags(entry.title) +
                entry.tags.map { it.lowercase().trim() }).distinct()

        val mediaJson = json.encodeToString(entry.mediaUris)

        val entity = DiaryEntryEntity(
            id = entryId,
            title = entry.title,
            content = entry.content,
            notebookId = entry.notebookId,
            colorHex = entry.colorHex,
            createdAt = createdAt,
            updatedAt = now,
            latitude = entry.latitude,
            longitude = entry.longitude,
            locationName = entry.locationName,
            mediaUrisJson = mediaJson,
            isPinned = entry.isPinned,
            isFavorite = entry.isFavorite,
            moodEmoji = entry.moodEmoji
        )

        // 2. Insert or update entry
        entryDao.insertEntry(entity)

        // 3. Update tags
        tagDao.deleteCrossRefsForEntry(entryId)
        if (parsedTags.isNotEmpty()) {
            val tagEntities = parsedTags.map { TagEntity(it, 1, now) }
            tagDao.insertTags(tagEntities)
            val refs = parsedTags.map { EntryTagCrossRef(entryId, it) }
            tagDao.insertCrossRefs(refs)
        }
        tagDao.recalculateTagCounts()
        tagDao.cleanupUnusedTags()

        return entryId
    }

    override suspend fun deleteEntry(id: String) {
        entryDao.deleteEntryById(id)
        tagDao.deleteCrossRefsForEntry(id)
        tagDao.recalculateTagCounts()
        tagDao.cleanupUnusedTags()
    }

    // Notebooks
    override fun getAllNotebooks(): Flow<List<Notebook>> {
        return notebookDao.getAllNotebooksFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveNotebook(notebook: Notebook) {
        notebookDao.insertNotebook(NotebookEntity.fromDomain(notebook))
    }

    override suspend fun deleteNotebook(id: String) {
        val notebook = notebookDao.getNotebookById(id)
        if (notebook != null && !notebook.isDefault) {
            notebookDao.deleteNotebook(notebook)
        }
    }

    override suspend fun getNotebookById(id: String): Notebook? {
        return notebookDao.getNotebookById(id)?.toDomain()
    }

    override suspend fun ensureDefaultNotebooks() {
        val count = notebookDao.getNotebookCount()
        if (count == 0) {
            val defaults = listOf(
                NotebookEntity("default_personal", "Personal", "Everyday thoughts and reflections", "book", "#2E7D32", System.currentTimeMillis(), true),
                NotebookEntity("default_ideas", "Ideas", "Inventions, inspirations, brainstorms", "lightbulb", "#1565C0", System.currentTimeMillis(), false),
                NotebookEntity("default_reflections", "Reflections", "Deep gratitude and life lessons", "spa", "#6A1B9A", System.currentTimeMillis(), false)
            )
            notebookDao.insertNotebooks(defaults)
        }
    }

    // Tags
    override fun getAllTags(): Flow<List<Tag>> {
        return tagDao.getAllTagsFlow().map { list -> list.map { it.toDomain() } }
    }

    // Export / Import
    override suspend fun getExportData(): ExportData {
        val allEntities = entryDao.getAllEntries()
        val entries = allEntities.map { entity ->
            val tags = tagDao.getTagsForEntry(entity.id)
            val media = decodeMediaList(entity.mediaUrisJson)
            entity.toDomain(tags, media)
        }
        val notebooks = notebookDao.getAllNotebooks().map { it.toDomain() }
        val tags = tagDao.getAllTags().map { it.toDomain() }

        return ExportData(
            version = 1,
            app = "FrictionFreeDiary",
            exportedAt = System.currentTimeMillis(),
            entries = entries,
            notebooks = notebooks,
            tags = tags
        )
    }

    override suspend fun importData(exportData: ExportData, overwriteExisting: Boolean) {
        // 1. Import notebooks
        val notebookEntities = exportData.notebooks.map { NotebookEntity.fromDomain(it) }
        notebookDao.insertNotebooks(notebookEntities)

        // 2. Import entries
        for (entry in exportData.entries) {
            val existing = entryDao.getEntryById(entry.id)
            if (existing == null || overwriteExisting) {
                saveEntry(entry)
            }
        }
    }

    private fun decodeMediaList(jsonStr: String): List<String> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
