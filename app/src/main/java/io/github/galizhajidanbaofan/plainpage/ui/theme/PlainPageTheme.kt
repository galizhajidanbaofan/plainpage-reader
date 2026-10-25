package io.github.galizhajidanbaofan.plainpage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** 当前生效的阅读配色，供阅读页、书目卡片等读取。 */
val LocalThemeTokens = staticCompositionLocalOf { ThemeTokensCatalog.Light }

/**
 * 把 [ThemeTokens] 映射成 Material3 的 ColorScheme，使 Compose 组件（按钮、导航栏等）
 * 也跟随阅读主题，而不是固定成蓝色默认值。
 */
@Composable
fun PlainPageTheme(
    tokens: ThemeTokens,
    content: @Composable () -> Unit,
) {
    val scheme = if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.paper,
            background = tokens.paper,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surface,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.textSecondary,
            outlineVariant = tokens.divider,
            // 容器色也跟随阅读主题：FAB / 选中态原本会退回 Material 默认紫色，
            // 在绿色强调色的界面里非常刺眼（离屏截图里一眼看出来的）。
            primaryContainer = tokens.accent,
            onPrimaryContainer = tokens.paper,
            secondaryContainer = tokens.surface,
            onSecondaryContainer = tokens.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.paper,
            background = tokens.paper,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surface,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.textSecondary,
            outlineVariant = tokens.divider,
            // 容器色也跟随阅读主题：FAB / 选中态原本会退回 Material 默认紫色，
            // 在绿色强调色的界面里非常刺眼（离屏截图里一眼看出来的）。
            primaryContainer = tokens.accent,
            onPrimaryContainer = tokens.paper,
            secondaryContainer = tokens.surface,
            onSecondaryContainer = tokens.textPrimary,
        )
    }

    CompositionLocalProvider(LocalThemeTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
