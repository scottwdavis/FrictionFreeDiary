package com.frictionfree.diary.data.repository

import com.frictionfree.diary.data.local.DiaryDatabase
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

interface DiaryRepository {
    fun getAllEntries(isArchived: Boolean = false): Flow<List<DiaryEntry>>
    fun getEntryById(id: String): Flow<DiaryEntry?>
    suspend fun getEntryByIdDirect(id: String): DiaryEntry?
    fun getEntriesByNotebook(notebookId: String, isArchived: Boolean = false): Flow<List<DiaryEntry>>
    fun getEntriesByTag(tagName: String, isArchived: Boolean = false): Flow<List<DiaryEntry>>
    fun searchEntries(query: String, isArchived: Boolean = false): Flow<List<DiaryEntry>>
    fun getEntriesInRange(startTime: Long, endTime: Long, isArchived: Boolean = false): Flow<List<DiaryEntry>>
    suspend fun saveEntry(entry: DiaryEntry): String
    suspend fun deleteEntry(id: String)
    suspend fun deleteEntries(entryIds: List<String>)
    suspend fun archiveEntries(entryIds: List<String>, isArchived: Boolean = true)
    suspend fun moveEntriesToNotebook(entryIds: List<String>, notebookId: String)

    // Notebooks
    fun getAllNotebooks(): Flow<List<Notebook>>
    fun getNotebookEntryCounts(): Flow<Map<String, Int>>
    suspend fun saveNotebook(notebook: Notebook)
    suspend fun deleteNotebook(id: String, deleteEntries: Boolean = false, targetNotebookId: String? = null)
    suspend fun getNotebookById(id: String): Notebook?
    suspend fun ensureDefaultNotebooks()

    // Tags
    fun getAllTags(): Flow<List<Tag>>

    // Export / Import
    suspend fun getExportData(): ExportData
    suspend fun importData(
        exportData: ExportData,
        overwriteExisting: Boolean = false,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)? = null
    )
}

class DiaryRepositoryImpl(
    private val databaseProvider: (() -> DiaryDatabase)? = null,
    private val explicitEntryDao: EntryDao? = null,
    private val explicitNotebookDao: NotebookDao? = null,
    private val explicitTagDao: TagDao? = null
) : DiaryRepository {

    constructor(databaseProvider: () -> DiaryDatabase) : this(
        databaseProvider = databaseProvider,
        explicitEntryDao = null,
        explicitNotebookDao = null,
        explicitTagDao = null
    )

    constructor(entryDao: EntryDao, notebookDao: NotebookDao, tagDao: TagDao) : this(
        databaseProvider = null,
        explicitEntryDao = entryDao,
        explicitNotebookDao = notebookDao,
        explicitTagDao = tagDao
    )

    private val entryDao: EntryDao get() = explicitEntryDao ?: databaseProvider?.invoke()?.entryDao()
        ?: error("No database or DAO provided")
    private val notebookDao: NotebookDao get() = explicitNotebookDao ?: databaseProvider?.invoke()?.notebookDao()
        ?: error("No database or DAO provided")
    private val tagDao: TagDao get() = explicitTagDao ?: databaseProvider?.invoke()?.tagDao()
        ?: error("No database or DAO provided")

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun getTagsForEntriesBatched(entryIds: List<String>): Map<String, List<String>> {
        if (entryIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, MutableList<String>>()
        entryIds.chunked(500).forEach { chunk ->
            val pairs = tagDao.getTagsForEntries(chunk)
            for (pair in pairs) {
                result.getOrPut(pair.entryId) { mutableListOf() }.add(pair.tagName)
            }
        }
        return result
    }

    private suspend fun mapEntitiesToDomain(entities: List<DiaryEntryEntity>): List<DiaryEntry> {
        if (entities.isEmpty()) return emptyList()
        val tagsMap = getTagsForEntriesBatched(entities.map { it.id })
        return entities.map { entity ->
            val tags = tagsMap[entity.id] ?: emptyList()
            val media = decodeMediaList(entity.mediaUrisJson)
            entity.toDomain(tags, media)
        }
    }

    override fun getAllEntries(isArchived: Boolean): Flow<List<DiaryEntry>> {
        return entryDao.getAllEntriesFlow(isArchived)
            .map { entities -> mapEntitiesToDomain(entities) }
            .flowOn(Dispatchers.IO)
    }

    override fun getEntryById(id: String): Flow<DiaryEntry?> {
        return entryDao.getEntryByIdFlow(id).map { entity ->
            if (entity == null) null
            else {
                val tags = tagDao.getTagsForEntry(entity.id)
                val media = decodeMediaList(entity.mediaUrisJson)
                entity.toDomain(tags, media)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getEntryByIdDirect(id: String): DiaryEntry? {
        val entity = entryDao.getEntryById(id) ?: return null
        val tags = tagDao.getTagsForEntry(entity.id)
        val media = decodeMediaList(entity.mediaUrisJson)
        return entity.toDomain(tags, media)
    }

    override fun getEntriesByNotebook(notebookId: String, isArchived: Boolean): Flow<List<DiaryEntry>> {
        return entryDao.getEntriesByNotebookFlow(notebookId, isArchived)
            .map { entities -> mapEntitiesToDomain(entities) }
            .flowOn(Dispatchers.IO)
    }

    override fun getEntriesByTag(tagName: String, isArchived: Boolean): Flow<List<DiaryEntry>> {
        return entryDao.getEntriesByTagFlow(tagName.lowercase(), isArchived)
            .map { entities -> mapEntitiesToDomain(entities) }
            .flowOn(Dispatchers.IO)
    }

    override fun searchEntries(query: String, isArchived: Boolean): Flow<List<DiaryEntry>> {
        if (query.isBlank()) return getAllEntries(isArchived)
        return entryDao.searchEntriesFlow(query.trim(), isArchived)
            .map { entities -> mapEntitiesToDomain(entities) }
            .flowOn(Dispatchers.IO)
    }

    override fun getEntriesInRange(startTime: Long, endTime: Long, isArchived: Boolean): Flow<List<DiaryEntry>> {
        return entryDao.getEntriesInRangeFlow(startTime, endTime, isArchived)
            .map { entities -> mapEntitiesToDomain(entities) }
            .flowOn(Dispatchers.IO)
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
            moodEmoji = entry.moodEmoji,
            isArchived = entry.isArchived
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

    override suspend fun deleteEntries(entryIds: List<String>) {
        if (entryIds.isEmpty()) return
        entryIds.chunked(500).forEach { chunk ->
            for (id in chunk) {
                tagDao.deleteCrossRefsForEntry(id)
            }
            entryDao.deleteEntriesByIds(chunk)
        }
        tagDao.recalculateTagCounts()
        tagDao.cleanupUnusedTags()
    }

    override suspend fun archiveEntries(entryIds: List<String>, isArchived: Boolean) {
        if (entryIds.isEmpty()) return
        entryIds.chunked(500).forEach { chunk ->
            entryDao.updateArchiveStatus(chunk, isArchived)
        }
    }

    override suspend fun moveEntriesToNotebook(entryIds: List<String>, notebookId: String) {
        if (entryIds.isEmpty()) return
        entryIds.chunked(500).forEach { chunk ->
            entryDao.updateNotebookForEntries(chunk, notebookId)
        }
    }

    // Notebooks
    override fun getAllNotebooks(): Flow<List<Notebook>> {
        return notebookDao.getAllNotebooksFlow().map { list -> list.map { it.toDomain() } }
    }

    override fun getNotebookEntryCounts(): Flow<Map<String, Int>> {
        return entryDao.getNotebookEntryCountsFlow()
            .map { list -> list.associate { it.notebookId to it.count } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun saveNotebook(notebook: Notebook) {
        notebookDao.insertNotebook(NotebookEntity.fromDomain(notebook))
    }

    override suspend fun deleteNotebook(id: String, deleteEntries: Boolean, targetNotebookId: String?) {
        val notebook = notebookDao.getNotebookById(id) ?: return
        if (notebook.isDefault) return

        if (deleteEntries) {
            val entryIds = entryDao.getEntryIdsByNotebook(id)
            deleteEntries(entryIds)
        } else {
            val targetId = if (!targetNotebookId.isNullOrBlank() && targetNotebookId != id) {
                targetNotebookId
            } else {
                notebookDao.getAllNotebooks().firstOrNull { it.isDefault && it.id != id }?.id ?: "default_personal"
            }
            entryDao.reassignNotebookForEntries(oldNotebookId = id, newNotebookId = targetId)
        }

        notebookDao.deleteNotebook(notebook)
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

    override suspend fun importData(
        exportData: ExportData,
        overwriteExisting: Boolean,
        onProgress: ((title: String, detail: String, progress: Float?) -> Unit)?
    ) {
        // 1. Import notebooks
        onProgress?.invoke("Importing Notebooks", "Setting up ${exportData.notebooks.size} notebooks...", null)
        val notebookEntities = exportData.notebooks.map { NotebookEntity.fromDomain(it) }
        notebookDao.insertNotebooks(notebookEntities)

        // 2. Import entries
        val total = exportData.entries.size
        for ((index, entry) in exportData.entries.withIndex()) {
            val existing = entryDao.getEntryById(entry.id)
            if (existing == null || overwriteExisting) {
                saveEntry(entry)
            }
            if (index % 5 == 0 || index == total - 1) {
                val fraction = if (total > 0) (index + 1).toFloat() / total else 1f
                onProgress?.invoke("Importing Entries", "${index + 1} of $total (${(fraction * 100).toInt()}%)", fraction)
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
