package com.frictionfree.diary.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.frictionfree.diary.ui.navigation.Screen

enum class DiaryNavDestination(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    TIMELINE(Screen.Timeline.route, "Stream", Icons.Default.ViewList),
    CALENDAR(Screen.Calendar.route, "Calendar", Icons.Default.CalendarMonth),
    TAGS(Screen.Tags.route, "Tags", Icons.Default.Tag),
    NOTEBOOKS(Screen.Notebooks.route, "Notebooks", Icons.Default.Book),
    SETTINGS(Screen.Settings.route, "Settings", Icons.Default.Settings)
}

@Composable
fun AdaptiveMainScaffold(
    currentRoute: String?,
    hideNavigationSuite: Boolean = false,
    onNavigateToDestination: (DiaryNavDestination) -> Unit,
    content: @Composable () -> Unit
) {
    val adaptiveLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
        currentWindowAdaptiveInfo()
    )
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val layoutType = when {
        hideNavigationSuite -> NavigationSuiteType.None
        isImeVisible && adaptiveLayoutType == NavigationSuiteType.NavigationBar -> NavigationSuiteType.None
        else -> adaptiveLayoutType
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            if (layoutType != NavigationSuiteType.None) {
                DiaryNavDestination.entries.forEach { dest ->
                    val isSelected = currentRoute == dest.route
                    item(
                        selected = isSelected,
                        onClick = { onNavigateToDestination(dest) },
                        icon = { Icon(dest.icon, contentDescription = dest.title) },
                        label = { Text(dest.title) }
                    )
                }
            }
        }
    ) {
        content()
    }
}
