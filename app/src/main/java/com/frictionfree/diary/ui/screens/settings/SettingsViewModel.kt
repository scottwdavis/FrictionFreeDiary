package com.frictionfree.diary.ui.screens.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.DiaryApplication
import com.frictionfree.diary.data.local.DiaryDatabase
import com.frictionfree.diary.data.repository.AppFontFamily
import com.frictionfree.diary.data.repository.AppFontSize
import com.frictionfree.diary.data.repository.AppTheme
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.data.repository.SettingsRepository
import com.frictionfree.diary.data.security.EncryptionHelper
import com.frictionfree.diary.utils.ImportManager
import com.frictionfree.diary.utils.ImportOutcome
import com.frictionfree.diary.utils.JsonExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val currentTheme: AppTheme = AppTheme.WARM_SEPIA,
    val currentFontFamily: AppFontFamily = AppFontFamily.SERIF,
    val currentFontSize: AppFontSize = AppFontSize.NORMAL,
    val instantCompose: Boolean = false,
    val geotaggingEnabled: Boolean = false,
    val biometricsEnabled: Boolean = false,
    val exportStatusMessage: String? = null,
    val latestExportFile: File? = null
)

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val diaryRepository: DiaryRepository
) : AndroidViewModel(application) {

    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _latestExportFile = MutableStateFlow<File?>(null)

    val uiState: StateFlow<SettingsUiState> = combine<Any?, SettingsUiState>(
        settingsRepository.theme,
        settingsRepository.fontFamily,
        settingsRepository.fontSize,
        settingsRepository.instantCompose,
        settingsRepository.geotaggingEnabled,
        settingsRepository.biometricsEnabled,
        _statusMessage,
        _latestExportFile
    ) { args ->
        SettingsUiState(
            currentTheme = args[0] as AppTheme,
            currentFontFamily = args[1] as AppFontFamily,
            currentFontSize = args[2] as AppFontSize,
            instantCompose = args[3] as Boolean,
            geotaggingEnabled = args[4] as Boolean,
            biometricsEnabled = args[5] as Boolean,
            exportStatusMessage = args[6] as String?,
            latestExportFile = args[7] as File?
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, SettingsUiState())

    fun setTheme(theme: AppTheme) {
        settingsRepository.setTheme(theme)
    }

    fun setFontFamily(fontFamily: AppFontFamily) {
        settingsRepository.setFontFamily(fontFamily)
    }

    fun setFontSize(fontSize: AppFontSize) {
        settingsRepository.setFontSize(fontSize)
    }

    fun setInstantCompose(enabled: Boolean) {
        settingsRepository.setInstantCompose(enabled)
    }

    fun setGeotagging(enabled: Boolean) {
        settingsRepository.setGeotaggingEnabled(enabled)
    }

    fun setBiometrics(enabled: Boolean, pin: String? = null) {
        val context = getApplication<Application>()
        if (enabled && !pin.isNullOrBlank()) {
            EncryptionHelper.setPin(context, pin)
            settingsRepository.setBiometricsEnabled(true)
            DiaryDatabase.closeAndReset()
            _statusMessage.value = "Encryption and Biometric lock enabled."
        } else if (!enabled) {
            EncryptionHelper.setEncryptionEnabled(context, false)
            settingsRepository.setBiometricsEnabled(false)
            DiaryDatabase.closeAndReset()
            _statusMessage.value = "Encryption lock disabled."
        }
    }

    fun exportToJson() {
        viewModelScope.launch {
            try {
                val data = diaryRepository.getExportData()
                val file = JsonExporter.writeExportFile(getApplication(), data)
                _latestExportFile.value = file
                _statusMessage.value = "Exported ${data.entries.size} entries to ${file.name}"
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.localizedMessage}"
            }
        }
    }

    fun importFromJson(uri: Uri) {
        importBackupOrArchive(uri)
    }

    fun importBackupOrArchive(uri: Uri, targetType: String? = null) {
        val app = getApplication<Application>() as? DiaryApplication
        val scope = app?.applicationScope ?: viewModelScope

        val initialTitle = when (targetType) {
            "Facebook" -> "Importing Facebook..."
            "DayOne" -> "Importing Day One..."
            "Native" -> "Restoring Backup..."
            else -> "Starting Import..."
        }

        scope.launch {
            ImportManager.updateProgress(
                isImporting = true,
                progress = null,
                title = initialTitle,
                detail = "Opening file..."
            )

            val outcome = JsonExporter.importBackupOrArchive(
                context = getApplication(),
                uri = uri,
                diaryRepository = diaryRepository,
                targetType = targetType,
                onProgress = { title, detail, progress ->
                    ImportManager.updateProgress(
                        isImporting = true,
                        progress = progress,
                        title = title,
                        detail = detail
                    )
                }
            )

            when (outcome) {
                is ImportOutcome.NativeSuccess -> {
                    val msg = "Imported ${outcome.entryCount} entries from backup successfully!"
                    _statusMessage.value = msg
                    ImportManager.updateProgress(
                        isImporting = false,
                        progress = 1f,
                        title = "Import Complete",
                        detail = msg,
                        isComplete = true
                    )
                    delay(4000)
                    ImportManager.dismiss()
                }
                is ImportOutcome.DayOneSuccess -> {
                    val photoText = if (outcome.photoCount > 0) " and ${outcome.photoCount} photos" else ""
                    val msg = "Imported ${outcome.entryCount} entries$photoText into '${outcome.notebookName}'"
                    _statusMessage.value = msg
                    ImportManager.updateProgress(
                        isImporting = false,
                        progress = 1f,
                        title = "Import Complete",
                        detail = msg,
                        isComplete = true
                    )
                    delay(4000)
                    ImportManager.dismiss()
                }
                is ImportOutcome.FacebookSuccess -> {
                    val photoText = if (outcome.photoCount > 0) " and ${outcome.photoCount} photos" else ""
                    val msg = "Imported ${outcome.entryCount} posts$photoText into '${outcome.notebookName}'"
                    _statusMessage.value = msg
                    ImportManager.updateProgress(
                        isImporting = false,
                        progress = 1f,
                        title = "Import Complete",
                        detail = msg,
                        isComplete = true
                    )
                    delay(4000)
                    ImportManager.dismiss()
                }
                is ImportOutcome.Error -> {
                    _statusMessage.value = outcome.message
                    ImportManager.updateProgress(
                        isImporting = false,
                        progress = null,
                        title = "Import Failed",
                        detail = outcome.message,
                        error = outcome.message
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
