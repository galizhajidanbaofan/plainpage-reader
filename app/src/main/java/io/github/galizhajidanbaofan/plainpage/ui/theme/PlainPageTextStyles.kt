package io.github.galizhajidanbaofan.plainpage.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 一套排版尺度。中文界面的难点不在字号，而在字距、行高和层级对比：
 * UI 用系统黑体（信息密度高、笔画清楚），正文用衬线（长文更省眼），
 * 元信息统一小号 + 加字距，让"说明性文字"和"内容"一眼能分开。
 *
 * 所有界面只从 [LocalTextStyles] 取样式，不写裸 sp。
 */
data class PlainPageTextStyles(
    /** 品牌字：简页。 */
    val brand: TextStyle,
    /** 栏目标签。小、疏、淡，用于「离线书架」「第 1 章」这类定位信息。 */
    val kicker: TextStyle,
    /** 页面标题（书架 / 最近 / 设置）。 */
    val screenTitle: TextStyle,
    /** 列表里的书名。 */
    val bookTitle: TextStyle,
    /** 网格里的书名，比列表小一号。 */
    val bookTitleCompact: TextStyle,
    val meta: TextStyle,
    /** 进度文字，强调色。 */
    val progress: TextStyle,
    /** 正文：衬线、大行高、两端对齐，首行缩进由 [readingIndent] 给。 */
    val readingBody: TextStyle,
    val readingIndent: TextUnit,
    /** 阅读页底部的页码行。 */
    val pageFooter: TextStyle,
    val tocTitle: TextStyle,
    val tocChapter: TextStyle,
    val tocPage: TextStyle,
    val emptyTitle: TextStyle,
    val emptyHint: TextStyle,
)

val LocalTextStyles = staticCompositionLocalOf { PlainPageTextStylesCatalog.Default }

object PlainPageTextStylesCatalog {

    private val sans = FontFamily.SansSerif
    private val serif = FontFamily.Serif

    /** 中文正文的避头尾与标点挤压交给系统排版器；关掉旧版字形放大保持字重一致。 */
    private val cjkPlatformStyle = PlatformTextStyle(includeFontPadding = false)

    val Default = PlainPageTextStyles(
        brand = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = 4.sp,
            platformStyle = cjkPlatformStyle,
        ),
        kicker = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            platformStyle = cjkPlatformStyle,
        ),
        screenTitle = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            letterSpacing = 0.6.sp,
            platformStyle = cjkPlatformStyle,
        ),
        bookTitle = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.2.sp,
            platformStyle = cjkPlatformStyle,
        ),
        bookTitleCompact = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.2.sp,
            platformStyle = cjkPlatformStyle,
        ),
        meta = TextStyle(
            fontFamily = sans,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            letterSpacing = 0.25.sp,
            platformStyle = cjkPlatformStyle,
        ),
        progress = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            platformStyle = cjkPlatformStyle,
        ),
        readingBody = TextStyle(
            fontFamily = serif,
            fontSize = 17.5.sp,
            lineHeight = 33.sp,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Justify,
            lineBreak = LineBreak.Paragraph,
            platformStyle = cjkPlatformStyle,
        ),
        readingIndent = 35.sp,
        pageFooter = TextStyle(
            fontFamily = sans,
            fontSize = 12.sp,
            letterSpacing = 1.1.sp,
            platformStyle = cjkPlatformStyle,
        ),
        tocTitle = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = 1.sp,
            platformStyle = cjkPlatformStyle,
        ),
        tocChapter = TextStyle(
            fontFamily = sans,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.2.sp,
            platformStyle = cjkPlatformStyle,
        ),
        tocPage = TextStyle(
            fontFamily = sans,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
            platformStyle = cjkPlatformStyle,
        ),
        emptyTitle = TextStyle(
            fontFamily = serif,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 1.sp,
            platformStyle = cjkPlatformStyle,
        ),
        emptyHint = TextStyle(
            fontFamily = sans,
            fontSize = 13.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.2.sp,
            platformStyle = cjkPlatformStyle,
        ),
    )
}
