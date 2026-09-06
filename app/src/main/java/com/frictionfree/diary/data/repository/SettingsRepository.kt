package com.frictionfree.diary.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme(val id: String, val displayName: String, val isDark: Boolean) {
    OLED_BLACK("oled_black", "OLED Black", true),
    OBSIDIAN("obsidian", "Obsidian Slate", true),
    PAPER_WHITE("paper_white", "Paper White", false),
    WARM_SEPIA("warm_sepia", "Warm Sepia", false),
    FOREST_PINE("forest_pine", "Forest Pine", true),
    NORDIC_FROST("nordic_frost", "Nordic Frost", true),
    ROSE_QUARTZ("rose_quartz", "Rose Quartz", false),
    SYSTEM_DYNAMIC("system_dynamic", "Material You (Dynamic)", false)
}

enum class AppFontFamily(val id: String, val displayName: String) {
    SANS_SERIF("sans", "Modern Sans"),
    SERIF("serif", "Classic Book (Serif)"),
    MONOSPACE("mono", "Clean Monospace")
}

enum class AppFontSize(val id: String, val displayName: String, val scaleFactor: Float) {
    COMPACT("compact", "Compact (85%)", 0.85f),
    NORMAL("normal", "Standard (100%)", 1.0f),
    LARGE("large", "Large (115%)", 1.15f),
    EXTRA_LARGE("extra_large", "Extra Large (130%)", 1.3f)
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("friction_free_settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(getSavedTheme())
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _fontFamily = MutableStateFlow(getSavedFontFamily())
    val fontFamily: StateFlow<AppFontFamily> = _fontFamily.asStateFlow()

    private val _fontSize = MutableStateFlow(getSavedFontSize())
    val fontSize: StateFlow<AppFontSize> = _fontSize.asStateFlow()

    private val _instantCompose = MutableStateFlow(prefs.getBoolean(KEY_INSTANT_COMPOSE, false))
    val instantCompose: StateFlow<Boolean> = _instantCompose.asStateFlow()

    private val _geotaggingEnabled = MutableStateFlow(prefs.getBoolean(KEY_GEOTAGGING, false))
    val geotaggingEnabled: StateFlow<Boolean> = _geotaggingEnabled.asStateFlow()

    private val _biometricsEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRICS, false))
    val biometricsEnabled: StateFlow<Boolean> = _biometricsEnabled.asStateFlow()

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.id).apply()
        _theme.value = theme
    }

    private fun getSavedTheme(): AppTheme {
        val id = prefs.getString(KEY_THEME, AppTheme.WARM_SEPIA.id)
        return AppTheme.entries.firstOrNull { it.id == id } ?: AppTheme.WARM_SEPIA
    }

    fun setFontFamily(fontFamily: AppFontFamily) {
        prefs.edit().putString(KEY_FONT_FAMILY, fontFamily.id).apply()
        _fontFamily.value = fontFamily
    }

    private fun getSavedFontFamily(): AppFontFamily {
        val id = prefs.getString(KEY_FONT_FAMILY, AppFontFamily.SERIF.id)
        return AppFontFamily.entries.firstOrNull { it.id == id } ?: AppFontFamily.SERIF
    }

    fun setFontSize(fontSize: AppFontSize) {
        prefs.edit().putString(KEY_FONT_SIZE, fontSize.id).apply()
        _fontSize.value = fontSize
    }

    private fun getSavedFontSize(): AppFontSize {
        val id = prefs.getString(KEY_FONT_SIZE, AppFontSize.NORMAL.id)
        return AppFontSize.entries.firstOrNull { it.id == id } ?: AppFontSize.NORMAL
    }

    fun setInstantCompose(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INSTANT_COMPOSE, enabled).apply()
        _instantCompose.value = enabled
    }

    fun setGeotaggingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GEOTAGGING, enabled).apply()
        _geotaggingEnabled.value = enabled
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRICS, enabled).apply()
        _biometricsEnabled.value = enabled
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
        _onboardingDone.value = done
    }

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val KEY_FONT_FAMILY = "app_font_family"
        private const val KEY_FONT_SIZE = "app_font_size"
        private const val KEY_INSTANT_COMPOSE = "instant_compose_mode"
        private const val KEY_GEOTAGGING = "geotagging_enabled"
        private const val KEY_BIOMETRICS = "biometrics_lock_enabled"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
    }
}
