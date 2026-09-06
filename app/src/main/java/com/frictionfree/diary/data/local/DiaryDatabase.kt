package com.frictionfree.diary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportFactory
import com.frictionfree.diary.data.local.dao.EntryDao
import com.frictionfree.diary.data.local.dao.NotebookDao
import com.frictionfree.diary.data.local.dao.TagDao
import com.frictionfree.diary.data.local.entities.DiaryEntryEntity
import com.frictionfree.diary.data.local.entities.EntryTagCrossRef
import com.frictionfree.diary.data.local.entities.NotebookEntity
import com.frictionfree.diary.data.local.entities.TagEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        DiaryEntryEntity::class,
        NotebookEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun notebookDao(): NotebookDao
    abstract fun tagDao(): TagDao

    companion object {
        private const val DB_NAME = "frictionfree_diary.db"

        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        fun getInstance(context: Context, passphrase: ByteArray? = null): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    DB_NAME
                )

                if (passphrase != null && passphrase.isNotEmpty()) {
                    val factory = SupportOpenHelperFactory(passphrase)
                    builder.openHelperFactory(factory)
                }

                builder.fallbackToDestructiveMigration()
                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Reset the database instance when encryption key changes
         */
        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
