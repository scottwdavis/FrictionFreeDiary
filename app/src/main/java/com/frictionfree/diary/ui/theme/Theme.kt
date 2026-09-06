package com.frictionfree.diary.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.frictionfree.diary.data.repository.AppFontFamily
import com.frictionfree.diary.data.repository.AppFontSize
import com.frictionfree.diary.data.repository.AppTheme

@Composable
fun FrictionFreeDiaryTheme(
    theme: AppTheme = AppTheme.WARM_SEPIA,
    fontFamily: AppFontFamily = AppFontFamily.SERIF,
    fontSize: AppFontSize = AppFontSize.NORMAL,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()

    val colorScheme = when {
        theme == AppTheme.SYSTEM_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getThemeColorScheme(theme)
    }

    val typography = createAppTypography(fontFamily, fontSize)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
