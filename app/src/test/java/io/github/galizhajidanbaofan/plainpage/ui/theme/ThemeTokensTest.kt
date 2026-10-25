package io.github.galizhajidanbaofan.plainpage.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun `body text keeps AAA contrast on every paper`() {
        ThemeTokensCatalog.all.forEach { tokens ->
            val ratio = contrastRatio(tokens.textPrimary, tokens.paper)
            assertTrue(
                "主题 ${tokens.theme} 正文对比度为 $ratio，低于 AAA 目标 7.0",
                ratio >= 7.0,
            )
        }
    }

    @Test
    fun `secondary text stays readable but visually lighter`() {
        ThemeTokensCatalog.all.forEach { tokens ->
            val primary = contrastRatio(tokens.textPrimary, tokens.paper)
            val secondary = contrastRatio(tokens.textSecondary, tokens.paper)
            assertTrue("主题 ${tokens.theme} 次要文本对比度 $secondary 低于 AA 4.5", secondary >= 4.5)
            assertTrue("主题 ${tokens.theme} 次要文本应当比正文更淡", secondary < primary)
        }
    }

    @Test
    fun `themes are visually distinct`() {
        val papers = ThemeTokensCatalog.all.map { it.paper }.toSet()
        assertEquals("三种主题的纸面色值必须互不相同", 3, papers.size)
    }

    @Test
    fun `only dark theme reports itself as dark`() {
        assertTrue(ThemeTokensCatalog.Dark.isDark)
        assertEquals(false, ThemeTokensCatalog.Light.isDark)
        assertEquals(false, ThemeTokensCatalog.Sepia.isDark)
    }

    @Test
    fun `catalog lookup matches theme identity`() {
        ReadingTheme.entries.forEach { theme ->
            assertEquals(theme, ThemeTokensCatalog.of(theme).theme)
        }
    }
}
