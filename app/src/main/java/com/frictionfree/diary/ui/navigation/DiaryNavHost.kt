package com.frictionfree.diary.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.frictionfree.diary.data.repository.DiaryRepository
import com.frictionfree.diary.data.repository.SettingsRepository
import com.frictionfree.diary.ui.screens.calendar.CalendarScreen
import com.frictionfree.diary.ui.screens.calendar.CalendarViewModel
import com.frictionfree.diary.ui.screens.editor.EditorScreen
import com.frictionfree.diary.ui.screens.editor.EditorViewModel
import com.frictionfree.diary.ui.screens.notebooks.NotebooksScreen
import com.frictionfree.diary.ui.screens.notebooks.NotebooksViewModel
import com.frictionfree.diary.ui.screens.onboarding.OnboardingScreen
import com.frictionfree.diary.ui.screens.settings.SettingsScreen
import com.frictionfree.diary.ui.screens.settings.SettingsViewModel
import com.frictionfree.diary.ui.screens.tags.TagsScreen
import com.frictionfree.diary.ui.screens.tags.TagsViewModel
import com.frictionfree.diary.ui.screens.timeline.TimelineScreen
import com.frictionfree.diary.ui.screens.timeline.TimelineViewModel
import com.frictionfree.diary.utils.ShareIntentHelper

@Composable
fun DiaryNavHost(
    navController: NavHostController,
    diaryRepository: DiaryRepository,
    settingsRepository: SettingsRepository,
    startDestination: String,
    sharedIncomingData: ShareIntentHelper.ParsedShareData? = null,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    settingsRepository.setOnboardingDone(true)
                    navController.navigate(Screen.Timeline.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Timeline.route) {
            val timelineVm: TimelineViewModel = viewModel {
                TimelineViewModel(diaryRepository)
            }
            TimelineScreen(
                viewModel = timelineVm,
                onNavigateToEditor = { entryId ->
                    navController.navigate(Screen.Editor.createRoute(entryId))
                },
                onNavigateToTags = {
                    navController.navigate(Screen.Tags.route)
                }
            )
        }

        composable(Screen.Calendar.route) {
            val calendarVm: CalendarViewModel = viewModel {
                CalendarViewModel(diaryRepository)
            }
            CalendarScreen(
                viewModel = calendarVm,
                onNavigateToEditor = { entryId ->
                    navController.navigate(Screen.Editor.createRoute(entryId))
                }
            )
        }

        composable(Screen.Tags.route) {
            val tagsVm: TagsViewModel = viewModel {
                TagsViewModel(diaryRepository)
            }
            TagsScreen(
                viewModel = tagsVm,
                onNavigateToEditor = { entryId ->
                    navController.navigate(Screen.Editor.createRoute(entryId))
                }
            )
        }

        composable(Screen.Notebooks.route) {
            val notebooksVm: NotebooksViewModel = viewModel {
                NotebooksViewModel(diaryRepository)
            }
            NotebooksScreen(
                viewModel = notebooksVm,
                onNavigateToEditor = { entryId ->
                    navController.navigate(Screen.Editor.createRoute(entryId))
                }
            )
        }

        composable(Screen.Settings.route) {
            val context = LocalContext.current
            val settingsVm: SettingsViewModel = viewModel {
                SettingsViewModel(
                    application = context.applicationContext as Application,
                    settingsRepository = settingsRepository,
                    diaryRepository = diaryRepository
                )
            }
            SettingsScreen(viewModel = settingsVm)
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("entryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId")
            val context = LocalContext.current
            val editorVm: EditorViewModel = viewModel {
                EditorViewModel(
                    application = context.applicationContext as Application,
                    diaryRepository = diaryRepository,
                    settingsRepository = settingsRepository
                )
            }

            LaunchedEffect(entryId, sharedIncomingData) {
                if (entryId != null) {
                    editorVm.loadEntry(entryId)
                } else if (sharedIncomingData != null) {
                    editorVm.initNewEntry(
                        initialTitle = sharedIncomingData.title,
                        initialContent = sharedIncomingData.content,
                        initialMedia = sharedIncomingData.mediaUris
                    )
                } else {
                    editorVm.initNewEntry()
                }
            }

            EditorScreen(
                viewModel = editorVm,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Timeline.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
