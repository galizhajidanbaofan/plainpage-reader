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
 *
 * 取向是「纸与墨」：纸面极淡、正文接近纯墨、分隔线只做一档发丝线，
 * 强调色用作印章色（进度、选中、可点文字），不用来铺大面积色块。
 */
data class ThemeTokens(
    val theme: ReadingTheme,
    /** 纸面：正文、阅读页、列表底。 */
    val paper: Color,
    /** 纸张阴影面：顶栏、底栏、底部导航这些"器具"用比纸面深一档的色。 */
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    /** 发丝分隔线，对比度刻意很低（约 1.3:1），只负责暗示结构。 */
    val divider: Color,
    /** 封面底纹：正文之外唯一允许出现的一点颜色，按书名取稳定色相。 */
    val coverWash: Color,
) {
    val isDark: Boolean get() = theme == ReadingTheme.DARK

    /** 落在强调色上面的字（按钮、印章底）。 */
    val onAccent: Color get() = paper
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

    /** 浅色：米白纸 + 墨黑字 + 松烟绿印章色。 */
    val Light = ThemeTokens(
        theme = ReadingTheme.LIGHT,
        paper = Color(0xFFFDFBF7),
        surface = Color(0xFFF6F2E9),
        textPrimary = Color(0xFF17150F),
        textSecondary = Color(0xFF5C574D),
        accent = Color(0xFF2F5D50),
        divider = Color(0xFFE4DED2),
        coverWash = Color(0xFFEFE9DC),
    )

    /** 深色：不是纯黑，带一点暖，长时间看不发青。 */
    val Dark = ThemeTokens(
        theme = ReadingTheme.DARK,
        paper = Color(0xFF121110),
        surface = Color(0xFF1B1A17),
        textPrimary = Color(0xFFEAE5DA),
        textSecondary = Color(0xFF9C968A),
        accent = Color(0xFF8FBFA9),
        divider = Color(0xFF2B2925),
        coverWash = Color(0xFF23211D),
    )

    /** 护眼：旧书纸的暖黄，墨色偏棕。 */
    val Sepia = ThemeTokens(
        theme = ReadingTheme.SEPIA,
        paper = Color(0xFFF6ECD6),
        surface = Color(0xFFEFE2C6),
        textPrimary = Color(0xFF33291B),
        textSecondary = Color(0xFF6A5A43),
        accent = Color(0xFF8A5526),
        divider = Color(0xFFDCCCB0),
        coverWash = Color(0xFFEADFC4),
    )

    val all: List<ThemeTokens> = listOf(Light, Dark, Sepia)

    fun of(theme: ReadingTheme): ThemeTokens = when (theme) {
        ReadingTheme.LIGHT -> Light
        ReadingTheme.DARK -> Dark
        ReadingTheme.SEPIA -> Sepia
    }
}
