package com.frictionfree.diary.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.frictionfree.diary.data.local.entities.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY isDefault DESC, name ASC")
    fun getAllNotebooksFlow(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks ORDER BY isDefault DESC, name ASC")
    suspend fun getAllNotebooks(): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE id = :id LIMIT 1")
    suspend fun getNotebookById(id: String): NotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebooks(notebooks: List<NotebookEntity>)

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Delete
    suspend fun deleteNotebook(notebook: NotebookEntity)

    @Query("SELECT COUNT(*) FROM notebooks")
    suspend fun getNotebookCount(): Int
}
