package com.frictionfree.diary.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.frictionfree.diary.data.local.entities.DiaryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE isArchived = :isArchived ORDER BY isPinned DESC, createdAt DESC")
    fun getAllEntriesFlow(isArchived: Boolean = false): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM entries WHERE isArchived = :isArchived ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllEntries(isArchived: Boolean = false): List<DiaryEntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): DiaryEntryEntity?

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    fun getEntryByIdFlow(id: String): Flow<DiaryEntryEntity?>

    @Query("SELECT * FROM entries WHERE notebookId = :notebookId AND isArchived = :isArchived ORDER BY isPinned DESC, createdAt DESC")
    fun getEntriesByNotebookFlow(notebookId: String, isArchived: Boolean = false): Flow<List<DiaryEntryEntity>>

    @Query("""
        SELECT e.* FROM entries e
        INNER JOIN entry_tag_cross_ref r ON e.id = r.entryId
        WHERE r.tagName = :tagName AND e.isArchived = :isArchived
        ORDER BY e.isPinned DESC, e.createdAt DESC
    """)
    fun getEntriesByTagFlow(tagName: String, isArchived: Boolean = false): Flow<List<DiaryEntryEntity>>

    @Query("""
        SELECT * FROM entries
        WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
          AND isArchived = :isArchived
        ORDER BY isPinned DESC, createdAt DESC
    """)
    fun searchEntriesFlow(query: String, isArchived: Boolean = false): Flow<List<DiaryEntryEntity>>

    @Query("""
        SELECT * FROM entries
        WHERE createdAt >= :startTime AND createdAt <= :endTime AND isArchived = :isArchived
        ORDER BY createdAt ASC
    """)
    fun getEntriesInRangeFlow(startTime: Long, endTime: Long, isArchived: Boolean = false): Flow<List<DiaryEntryEntity>>

    @Query("""
        SELECT * FROM entries
        WHERE createdAt >= :startTime AND createdAt <= :endTime AND isArchived = :isArchived
        ORDER BY createdAt ASC
    """)
    suspend fun getEntriesInRange(startTime: Long, endTime: Long, isArchived: Boolean = false): List<DiaryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DiaryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<DiaryEntryEntity>)

    @Update
    suspend fun updateEntry(entry: DiaryEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: DiaryEntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)

    @Query("DELETE FROM entries WHERE id IN (:entryIds)")
    suspend fun deleteEntriesByIds(entryIds: List<String>)

    @Query("UPDATE entries SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id IN (:entryIds)")
    suspend fun updateArchiveStatus(entryIds: List<String>, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE entries SET notebookId = :notebookId, updatedAt = :updatedAt WHERE id IN (:entryIds)")
    suspend fun updateNotebookForEntries(entryIds: List<String>, notebookId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM entries WHERE isArchived = 0")
    suspend fun getEntryCount(): Int

    @Query("SELECT notebookId, COUNT(*) as count FROM entries GROUP BY notebookId")
    fun getNotebookEntryCountsFlow(): Flow<List<NotebookEntryCount>>

    @Query("SELECT COUNT(*) FROM entries WHERE notebookId = :notebookId")
    suspend fun getEntryCountForNotebook(notebookId: String): Int

    @Query("SELECT id FROM entries WHERE notebookId = :notebookId")
    suspend fun getEntryIdsByNotebook(notebookId: String): List<String>

    @Query("UPDATE entries SET notebookId = :newNotebookId, updatedAt = :updatedAt WHERE notebookId = :oldNotebookId")
    suspend fun reassignNotebookForEntries(oldNotebookId: String, newNotebookId: String, updatedAt: Long = System.currentTimeMillis())
}

data class NotebookEntryCount(
    val notebookId: String,
    val count: Int
)
