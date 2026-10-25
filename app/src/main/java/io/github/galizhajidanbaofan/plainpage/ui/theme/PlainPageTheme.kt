package io.github.galizhajidanbaofan.plainpage.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 当前生效的阅读配色，供阅读页、书目卡片等读取。 */
val LocalThemeTokens = staticCompositionLocalOf { ThemeTokensCatalog.Light }

/**
 * 纸张不该有圆角。整套形状只保留 2~8dp 的极轻微圆角，
 * 让卡片、封面、浮层看起来像裁切的纸边，而不是 Material 默认的胶囊。
 */
private val PlainPageShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

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
            onPrimary = tokens.onAccent,
            primaryContainer = tokens.accent,
            onPrimaryContainer = tokens.onAccent,
            secondaryContainer = tokens.surface,
            onSecondaryContainer = tokens.textPrimary,
            background = tokens.paper,
            onBackground = tokens.textPrimary,
            surface = tokens.paper,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surface,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.textSecondary,
            outlineVariant = tokens.divider,
            error = ErrorInk,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.onAccent,
            primaryContainer = tokens.accent,
            onPrimaryContainer = tokens.onAccent,
            secondaryContainer = tokens.surface,
            onSecondaryContainer = tokens.textPrimary,
            background = tokens.paper,
            onBackground = tokens.textPrimary,
            surface = tokens.paper,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surface,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.textSecondary,
            outlineVariant = tokens.divider,
            error = ErrorInk,
        )
    }

    CompositionLocalProvider(
        LocalThemeTokens provides tokens,
        LocalTextStyles provides PlainPageTextStylesCatalog.Default,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = PlainPageShapes, content = content)
    }
}

/** 错误色用朱砂，和纸面配色是同一套语言，不用 Material 默认的玫红。 */
private val ErrorInk = Color(0xFFB3402F)
