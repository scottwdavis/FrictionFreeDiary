package com.frictionfree.diary

import android.app.Application
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val database: DiaryDatabase by lazy {
        val passphrase = EncryptionHelper.getDatabasePassphrase(this)
        DiaryDatabase.getInstance(this, passphrase)
    }

    val diaryRepository: DiaryRepository by lazy {
        DiaryRepositoryImpl(
            entryDao = database.entryDao(),
            notebookDao = database.notebookDao(),
            tagDao = database.tagDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure default notebooks are initialized
        applicationScope.launch {
            diaryRepository.ensureDefaultNotebooks()
        }
    }
}
