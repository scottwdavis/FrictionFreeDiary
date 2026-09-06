package com.frictionfree.diary.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Timeline : Screen("timeline")
    data object Calendar : Screen("calendar")
    data object Tags : Screen("tags")
    data object Notebooks : Screen("notebooks")
    data object Settings : Screen("settings")
    data object Editor : Screen("editor?entryId={entryId}") {
        fun createRoute(entryId: String? = null): String {
            return if (entryId != null) "editor?entryId=$entryId" else "editor"
        }
    }
}
