package com.frictionfree.diary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.frictionfree.diary.data.local.entities.EntryTagCrossRef
import com.frictionfree.diary.data.local.entities.TagEntity
import kotlinx.coroutines.flow.Flow

data class EntryTagTuple(
    val entryId: String,
    val tagName: String
)

@Dao
interface TagDao {
    @Query("""
        SELECT t.name, 
               (SELECT COUNT(*) FROM entry_tag_cross_ref r 
                INNER JOIN entries e ON r.entryId = e.id 
                WHERE r.tagName = t.name AND e.isArchived = 0) AS usageCount, 
               t.lastUsedAt 
        FROM tags t 
        WHERE (SELECT COUNT(*) FROM entry_tag_cross_ref r 
               INNER JOIN entries e ON r.entryId = e.id 
               WHERE r.tagName = t.name AND e.isArchived = 0) > 0
        ORDER BY usageCount DESC, t.name ASC
    """)
    fun getAllTagsFlow(): Flow<List<TagEntity>>

    @Query("""
        SELECT t.name, 
               (SELECT COUNT(*) FROM entry_tag_cross_ref r 
                INNER JOIN entries e ON r.entryId = e.id 
                WHERE r.tagName = t.name AND e.isArchived = 0) AS usageCount, 
               t.lastUsedAt 
        FROM tags t 
        WHERE (SELECT COUNT(*) FROM entry_tag_cross_ref r 
               INNER JOIN entries e ON r.entryId = e.id 
               WHERE r.tagName = t.name AND e.isArchived = 0) > 0
        ORDER BY usageCount DESC, t.name ASC
    """)
    suspend fun getAllTags(): List<TagEntity>

    @Query("SELECT tagName FROM entry_tag_cross_ref WHERE entryId = :entryId")
    suspend fun getTagsForEntry(entryId: String): List<String>

    @Query("SELECT entryId, tagName FROM entry_tag_cross_ref WHERE entryId IN (:entryIds)")
    suspend fun getTagsForEntries(entryIds: List<String>): List<EntryTagTuple>

    @Query("SELECT tagName FROM entry_tag_cross_ref WHERE entryId = :entryId")
    fun getTagsForEntryFlow(entryId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: EntryTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<EntryTagCrossRef>)

    @Query("DELETE FROM entry_tag_cross_ref WHERE entryId = :entryId")
    suspend fun deleteCrossRefsForEntry(entryId: String)

    @Query("DELETE FROM entry_tag_cross_ref WHERE entryId IN (:entryIds)")
    suspend fun deleteCrossRefsForEntries(entryIds: List<String>)

    @Query("DELETE FROM entry_tag_cross_ref WHERE entryId IN (SELECT id FROM entries WHERE notebookId = :notebookId)")
    suspend fun deleteCrossRefsForNotebook(notebookId: String)

    @Query("UPDATE tags SET usageCount = (SELECT COUNT(*) FROM entry_tag_cross_ref WHERE tagName = tags.name)")
    suspend fun recalculateTagCounts()

    @Query("DELETE FROM tags WHERE name NOT IN (SELECT DISTINCT tagName FROM entry_tag_cross_ref)")
    suspend fun cleanupUnusedTags()
}
