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
    @Query("SELECT * FROM entries ORDER BY isPinned DESC, createdAt DESC")
    fun getAllEntriesFlow(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM entries ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllEntries(): List<DiaryEntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): DiaryEntryEntity?

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    fun getEntryByIdFlow(id: String): Flow<DiaryEntryEntity?>

    @Query("SELECT * FROM entries WHERE notebookId = :notebookId ORDER BY isPinned DESC, createdAt DESC")
    fun getEntriesByNotebookFlow(notebookId: String): Flow<List<DiaryEntryEntity>>

    @Query("""
        SELECT e.* FROM entries e
        INNER JOIN entry_tag_cross_ref r ON e.id = r.entryId
        WHERE r.tagName = :tagName
        ORDER BY e.isPinned DESC, e.createdAt DESC
    """)
    fun getEntriesByTagFlow(tagName: String): Flow<List<DiaryEntryEntity>>

    @Query("""
        SELECT * FROM entries
        WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, createdAt DESC
    """)
    fun searchEntriesFlow(query: String): Flow<List<DiaryEntryEntity>>

    @Query("""
        SELECT * FROM entries
        WHERE createdAt >= :startTime AND createdAt <= :endTime
        ORDER BY createdAt ASC
    """)
    fun getEntriesInRangeFlow(startTime: Long, endTime: Long): Flow<List<DiaryEntryEntity>>

    @Query("""
        SELECT * FROM entries
        WHERE createdAt >= :startTime AND createdAt <= :endTime
        ORDER BY createdAt ASC
    """)
    suspend fun getEntriesInRange(startTime: Long, endTime: Long): List<DiaryEntryEntity>

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

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun getEntryCount(): Int
}
