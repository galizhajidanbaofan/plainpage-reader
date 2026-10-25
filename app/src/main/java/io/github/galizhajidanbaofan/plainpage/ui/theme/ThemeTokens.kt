package io.github.galizhajidanbaofan.plainpage.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 阅读主题。与 Material 的 ColorScheme 解耦：这里的主题决定"纸面"配色，
 * 书架外壳仍跟随同一套 token，保证切换主题时不会有一半界面没跟上。
 */
enum class ReadingTheme { LIGHT, DARK, SEPIA }

/**
 * 阅读界面的一套配色。所有业务代码只依赖这些语义名，不直接写死色值，
 * 也不在业务层判断"当前是不是深色"。
 */
data class ThemeTokens(
    val theme: ReadingTheme,
    val paper: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val divider: Color,
) {
    val isDark: Boolean get() = theme == ReadingTheme.DARK
}

/** WCAG 相对亮度。 */
fun Color.relativeLuminance(): Double {
    fun linear(channel: Float): Double {
        val v = channel.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
}

/** WCAG 对比度，1.0 ~ 21.0。正文目标 ≥ 4.5，本项目的浅色/护眼主题按 ≥ 7.0 设计。 */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** 三种阅读主题的实际色值。改动这里必须同步跑 ThemeTokensTest。 */
object ThemeTokensCatalog {

    val Light = ThemeTokens(
        theme = ReadingTheme.LIGHT,
        paper = Color(0xFFFFFFFF),
        surface = Color(0xFFF5F5F3),
        textPrimary = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF5A5A5A),
        accent = Color(0xFF2F6F5E),
        divider = Color(0xFFE2E2E2),
    )

    val Dark = ThemeTokens(
        theme = ReadingTheme.DARK,
        paper = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        textPrimary = Color(0xFFE6E2DA),
        textSecondary = Color(0xFFA8A29A),
        accent = Color(0xFF7FB6A4),
        divider = Color(0xFF2C2C2C),
    )

    val Sepia = ThemeTokens(
        theme = ReadingTheme.SEPIA,
        paper = Color(0xFFF4ECD8),
        surface = Color(0xFFEDE3C9),
        textPrimary = Color(0xFF3B2F1E),
        textSecondary = Color(0xFF6B5B45),
        accent = Color(0xFF8C5A2B),
        divider = Color(0xFFD8CBAE),
    )

    val all: List<ThemeTokens> = listOf(Light, Dark, Sepia)

    fun of(theme: ReadingTheme): ThemeTokens = when (theme) {
        ReadingTheme.LIGHT -> Light
        ReadingTheme.DARK -> Dark
        ReadingTheme.SEPIA -> Sepia
    }
}
