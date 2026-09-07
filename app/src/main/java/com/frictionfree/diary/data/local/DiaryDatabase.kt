package com.frictionfree.diary.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.frictionfree.diary.data.local.dao.EntryDao
import com.frictionfree.diary.data.local.dao.NotebookDao
import com.frictionfree.diary.data.local.dao.TagDao
import com.frictionfree.diary.data.local.entities.DiaryEntryEntity
import com.frictionfree.diary.data.local.entities.EntryTagCrossRef
import com.frictionfree.diary.data.local.entities.NotebookEntity
import com.frictionfree.diary.data.local.entities.TagEntity
import com.frictionfree.diary.data.security.EncryptionHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(
    entities = [
        DiaryEntryEntity::class,
        NotebookEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun notebookDao(): NotebookDao
    abstract fun tagDao(): TagDao

    companion object {
        private const val TAG = "DiaryDatabase"
        private const val DB_NAME = "frictionfree_diary.db"

        init {
            try {
                System.loadLibrary("sqlcipher")
            } catch (t: Throwable) {
                Log.e(TAG, "Initial sqlcipher load failed", t)
            }
        }

        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        fun isDatabasePlaintext(dbFile: File): Boolean {
            if (!dbFile.exists() || dbFile.length() < 16) return false
            return try {
                dbFile.inputStream().use { input ->
                    val header = ByteArray(16)
                    val bytesRead = input.read(header)
                    if (bytesRead == 16) {
                        String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking database header", e)
                false
            }
        }

        private fun encryptDatabase(context: Context, dbFile: File, passphraseString: String): Boolean {
            if (!dbFile.exists() || dbFile.length() == 0L) return true
            if (!isDatabasePlaintext(dbFile)) return true // Already encrypted

            Log.i(TAG, "Migrating plaintext database to SQLCipher encrypted format...")
            val tempEncryptedFile = File(dbFile.parentFile, "${dbFile.name}.enc_tmp")
            if (tempEncryptedFile.exists()) tempEncryptedFile.delete()

            val walFile = File(dbFile.parentFile, "${dbFile.name}-wal")
            val shmFile = File(dbFile.parentFile, "${dbFile.name}-shm")
            val backupFile = File(dbFile.parentFile, "${dbFile.name}.pre_enc_bak")

            return try {
                try {
                    System.loadLibrary("sqlcipher")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed loading sqlcipher in encryptDatabase", t)
                }

                // Open plaintext DB using empty password in SQLCipher
                val db = SQLiteDatabase.openOrCreateDatabase(
                    dbFile,
                    "",
                    null,
                    null
                )

                try {
                    try {
                        db.rawExecSQL("PRAGMA wal_checkpoint(FULL);")
                    } catch (e: Exception) {
                        Log.w(TAG, "wal_checkpoint FULL failed, trying TRUNCATE", e)
                        try { db.rawExecSQL("PRAGMA wal_checkpoint(TRUNCATE);") } catch (_: Exception) {}
                    }

                    val safeKey = passphraseString.replace("'", "''")
                    db.rawExecSQL("ATTACH DATABASE '${tempEncryptedFile.absolutePath}' AS encrypted KEY '$safeKey';")
                    db.rawExecSQL("SELECT sqlcipher_export('encrypted');")
                    db.rawExecSQL("DETACH DATABASE encrypted;")
                } finally {
                    db.close()
                }

                if (tempEncryptedFile.exists() && tempEncryptedFile.length() > 0) {
                    // Backup original file
                    if (backupFile.exists()) backupFile.delete()
                    dbFile.copyTo(backupFile, overwrite = true)

                    // Remove original plaintext file and WAL/SHM
                    dbFile.delete()
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()

                    val renamed = tempEncryptedFile.renameTo(dbFile)
                    if (renamed) {
                        if (backupFile.exists()) backupFile.delete()
                        Log.i(TAG, "Database encryption migration completed successfully")
                        true
                    } else {
                        Log.e(TAG, "Failed renaming encrypted file, restoring backup")
                        if (backupFile.exists()) backupFile.renameTo(dbFile)
                        false
                    }
                } else {
                    Log.e(TAG, "Encrypted temporary file was not created or is empty")
                    if (tempEncryptedFile.exists()) tempEncryptedFile.delete()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate database to encrypted format", e)
                if (tempEncryptedFile.exists()) tempEncryptedFile.delete()
                if (backupFile.exists() && !dbFile.exists()) {
                    backupFile.renameTo(dbFile)
                }
                false
            }
        }

        private fun decryptDatabase(context: Context, dbFile: File, passphraseString: String): Boolean {
            if (!dbFile.exists() || dbFile.length() == 0L) return true
            if (isDatabasePlaintext(dbFile)) return true // Already plaintext

            Log.i(TAG, "Migrating SQLCipher encrypted database to plaintext format...")
            val tempPlaintextFile = File(dbFile.parentFile, "${dbFile.name}.plain_tmp")
            if (tempPlaintextFile.exists()) tempPlaintextFile.delete()

            val walFile = File(dbFile.parentFile, "${dbFile.name}-wal")
            val shmFile = File(dbFile.parentFile, "${dbFile.name}-shm")
            val backupFile = File(dbFile.parentFile, "${dbFile.name}.pre_dec_bak")

            return try {
                try {
                    System.loadLibrary("sqlcipher")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed loading sqlcipher in decryptDatabase", t)
                }

                val db = SQLiteDatabase.openOrCreateDatabase(
                    dbFile,
                    passphraseString,
                    null,
                    null
                )

                try {
                    try {
                        db.rawExecSQL("PRAGMA wal_checkpoint(FULL);")
                    } catch (e: Exception) {
                        Log.w(TAG, "wal_checkpoint FULL failed, trying TRUNCATE", e)
                        try { db.rawExecSQL("PRAGMA wal_checkpoint(TRUNCATE);") } catch (_: Exception) {}
                    }

                    db.rawExecSQL("ATTACH DATABASE '${tempPlaintextFile.absolutePath}' AS plaintext KEY '';")
                    db.rawExecSQL("SELECT sqlcipher_export('plaintext');")
                    db.rawExecSQL("DETACH DATABASE plaintext;")
                } finally {
                    db.close()
                }

                if (tempPlaintextFile.exists() && tempPlaintextFile.length() > 0) {
                    if (backupFile.exists()) backupFile.delete()
                    dbFile.copyTo(backupFile, overwrite = true)

                    dbFile.delete()
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()

                    val renamed = tempPlaintextFile.renameTo(dbFile)
                    if (renamed) {
                        if (backupFile.exists()) backupFile.delete()
                        Log.i(TAG, "Database decryption migration completed successfully")
                        true
                    } else {
                        Log.e(TAG, "Failed renaming plaintext file, restoring backup")
                        if (backupFile.exists()) backupFile.renameTo(dbFile)
                        false
                    }
                } else {
                    Log.e(TAG, "Plaintext temporary file was not created or is empty")
                    if (tempPlaintextFile.exists()) tempPlaintextFile.delete()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate database to plaintext format", e)
                if (tempPlaintextFile.exists()) tempPlaintextFile.delete()
                if (backupFile.exists() && !dbFile.exists()) {
                    backupFile.renameTo(dbFile)
                }
                false
            }
        }

        fun getInstance(context: Context, passphrase: ByteArray? = null): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    System.loadLibrary("sqlcipher")
                } catch (e: Throwable) {
                    Log.e(TAG, "Could not load sqlcipher library", e)
                }

                val dbFile = context.getDatabasePath(DB_NAME)
                val passphraseString = EncryptionHelper.getStoredPassphraseString(context)

                var effectivePassphrase = passphrase
                if (dbFile.exists() && dbFile.length() > 0) {
                    val isPlaintext = isDatabasePlaintext(dbFile)
                    if (effectivePassphrase != null && effectivePassphrase.isNotEmpty() && isPlaintext) {
                        // Plaintext database needs to be encrypted
                        if (passphraseString != null) {
                            val success = encryptDatabase(context, dbFile, passphraseString)
                            if (!success) {
                                Log.e(TAG, "Failed to encrypt database, falling back to plaintext Room")
                                effectivePassphrase = null
                            }
                        } else {
                            effectivePassphrase = null
                        }
                    } else if ((effectivePassphrase == null || effectivePassphrase.isEmpty()) && !isPlaintext) {
                        // Encrypted database needs to be decrypted
                        if (passphraseString != null) {
                            val success = decryptDatabase(context, dbFile, passphraseString)
                            if (!success) {
                                Log.e(TAG, "Failed to decrypt database, keeping SQLCipher key")
                                effectivePassphrase = passphraseString.toByteArray(Charsets.UTF_8)
                            }
                        }
                    }
                }

                val migration1To2 = object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE entries ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_entries_isArchived ON entries (isArchived)")
                    }
                }

                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    DB_NAME
                ).addMigrations(migration1To2)

                if (effectivePassphrase != null && effectivePassphrase.isNotEmpty()) {
                    val factory = SupportOpenHelperFactory(effectivePassphrase)
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
