package com.frictionfree.diary.ui.screens.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frictionfree.diary.data.repository.AppFontFamily
import com.frictionfree.diary.data.repository.AppFontSize
import com.frictionfree.diary.data.repository.AppTheme
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.data.repository.SettingsRepository
import com.frictionfree.diary.data.security.EncryptionHelper
import com.frictionfree.diary.utils.JsonExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.theme,
        settingsRepository.fontFamily,
        settingsRepository.fontSize,
        settingsRepository.instantCompose,
        settingsRepository.geotaggingEnabled,
        settingsRepository.biometricsEnabled,
        _statusMessage,
        _latestExportFile
    ) { theme, font, size, instant, geo, bio, msg, file ->
        SettingsUiState(
            currentTheme = theme,
            currentFontFamily = font,
            currentFontSize = size,
            instantCompose = instant,
            geotaggingEnabled = geo,
            biometricsEnabled = bio,
            exportStatusMessage = msg,
            latestExportFile = file
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
        if (enabled && pin != null && pin.isNotBlank()) {
            EncryptionHelper.setPin(context, pin)
            settingsRepository.setBiometricsEnabled(true)
            _statusMessage.value = "Encryption and Biometric lock enabled."
        } else if (!enabled) {
            EncryptionHelper.setEncryptionEnabled(context, false)
            settingsRepository.setBiometricsEnabled(false)
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
        viewModelScope.launch {
            try {
                val data = JsonExporter.readFromUri(getApplication(), uri)
                if (data != null) {
                    diaryRepository.importData(data, overwriteExisting = false)
                    _statusMessage.value = "Imported ${data.entries.size} entries successfully!"
                } else {
                    _statusMessage.value = "Failed to parse JSON backup file."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.localizedMessage}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
