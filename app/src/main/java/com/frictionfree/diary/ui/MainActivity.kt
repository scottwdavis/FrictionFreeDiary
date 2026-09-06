package com.frictionfree.diary.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.frictionfree.diary.DiaryApplication
import com.frictionfree.diary.data.security.BiometricAuthManager
import com.frictionfree.diary.data.security.EncryptionHelper
import com.frictionfree.diary.ui.adaptive.AdaptiveMainScaffold
import com.frictionfree.diary.ui.components.BiometricLockScreen
import com.frictionfree.diary.ui.navigation.DiaryNavHost
import com.frictionfree.diary.ui.navigation.Screen
import com.frictionfree.diary.ui.theme.FrictionFreeDiaryTheme
import com.frictionfree.diary.utils.ShareIntentHelper

class MainActivity : FragmentActivity() {

    private var sharedData by mutableStateOf<ShareIntentHelper.ParsedShareData?>(null)
    private var isAppLocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DiaryApplication
        val isBiometricsConfigured = EncryptionHelper.isEncryptionEnabled(this)
        isAppLocked = isBiometricsConfigured

        // Parse incoming share intent or shortcut
        handleIncomingIntent(intent)

        setContent {
            val theme by app.settingsRepository.theme.collectAsState()
            val fontFamily by app.settingsRepository.fontFamily.collectAsState()
            val fontSize by app.settingsRepository.fontSize.collectAsState()
            val instantCompose by app.settingsRepository.instantCompose.collectAsState()
            val onboardingDone by app.settingsRepository.onboardingDone.collectAsState()

            FrictionFreeDiaryTheme(
                theme = theme,
                fontFamily = fontFamily,
                fontSize = fontSize
            ) {
                if (isAppLocked) {
                    BiometricLockScreen(
                        onAuthenticateBiometric = {
                            BiometricAuthManager.promptBiometric(
                                activity = this@MainActivity,
                                onSuccess = { isAppLocked = false },
                                onError = { /* User can use PIN or retry */ }
                            )
                        },
                        onVerifyPin = { pin ->
                            val valid = EncryptionHelper.verifyPin(this@MainActivity, pin)
                            if (valid) isAppLocked = false
                            valid
                        },
                        onUnlocked = { isAppLocked = false }
                    )
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Determine start destination
                    val startDest = when {
                        !onboardingDone -> Screen.Onboarding.route
                        sharedData != null -> Screen.Editor.route
                        intent.getStringExtra("shortcut_action") == "new_entry" -> Screen.Editor.route
                        instantCompose -> Screen.Editor.route
                        else -> Screen.Timeline.route
                    }

                    // Hide navigation rail/bar when inside Editor or Onboarding
                    val isFullScreen = currentRoute?.startsWith("editor") == true || currentRoute == Screen.Onboarding.route

                    if (isFullScreen) {
                        DiaryNavHost(
                            navController = navController,
                            diaryRepository = app.diaryRepository,
                            settingsRepository = app.settingsRepository,
                            startDestination = startDest,
                            sharedIncomingData = sharedData
                        )
                    } else {
                        AdaptiveMainScaffold(
                            currentRoute = currentRoute,
                            onNavigateToDestination = { dest ->
                                navController.navigate(dest.route) {
                                    popUpTo(Screen.Timeline.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        ) {
                            DiaryNavHost(
                                navController = navController,
                                diaryRepository = app.diaryRepository,
                                settingsRepository = app.settingsRepository,
                                startDestination = startDest,
                                sharedIncomingData = sharedData
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        sharedData = ShareIntentHelper.parseIncomingIntent(this, intent)
    }
}
