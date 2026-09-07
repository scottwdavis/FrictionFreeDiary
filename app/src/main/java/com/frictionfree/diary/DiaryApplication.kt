package com.frictionfree.diary

import android.app.Application
import android.util.Log
import com.frictionfree.diary.data.local.DiaryDatabase
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.data.repository.DiaryRepositoryImpl
import com.frictionfree.diary.data.repository.SettingsRepository
import com.frictionfree.diary.data.security.EncryptionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DiaryApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val database: DiaryDatabase
        get() {
            val passphrase = EncryptionHelper.getDatabasePassphrase(this)
            return DiaryDatabase.getInstance(this, passphrase)
        }

    val diaryRepository: DiaryRepository by lazy {
        DiaryRepositoryImpl(
            databaseProvider = { database }
        )
    }

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (t: Throwable) {
            Log.e("DiaryApplication", "Failed to load sqlcipher native library", t)
        }
        // Ensure default notebooks are initialized
        applicationScope.launch {
            diaryRepository.ensureDefaultNotebooks()
        }
    }
}
