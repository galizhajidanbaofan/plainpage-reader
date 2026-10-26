package io.github.galizhajidanbaofan.plainpage.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.github.galizhajidanbaofan.plainpage.ui.AppearanceState
import io.github.galizhajidanbaofan.plainpage.ui.LocalAppearance
import io.github.galizhajidanbaofan.plainpage.ui.ReadingMode
import io.github.galizhajidanbaofan.plainpage.ui.theme.PlainPageTheme
import io.github.galizhajidanbaofan.plainpage.ui.theme.ThemeTokensCatalog
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 翻页手势的行为测试：回答「手势到底有没有接到回调上」，
 * 而不是「界面长什么样」（那是截图测试的事）。
 *
 * 这条测试替的设备端手动验收：本机跑不起模拟器，手势一类的交互至少要在 JVM 上有一层证据。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ReaderGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private var prev = 0
    private var next = 0

    private fun state() = ReaderUiState.Ready(
        title = "冬夜札记",
        chapterTitle = "第一章 雪夜",
        chapters = listOf(ReaderChapter("第一章 雪夜", 0), ReaderChapter("第二章 旧书摊", 1_200)),
        text = "第一章 雪夜\n\n那年冬天来得格外早。",
        pageIndex = 1,
        pageCount = 12,
        fraction = 0.1f,
        canPrev = true,
        canNext = true,
        lossyDecoding = false,
        charCount = 1_200,
        anchorChar = 1_200,
    )

    private fun render(mode: ReadingMode) {
        prev = 0
        next = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides AppearanceState(readingMode = mode)) {
                PlainPageTheme(tokens = ThemeTokensCatalog.Light) {
                    ReaderContent(
                        state = state(),
                        onBack = {},
                        onPrevPage = { prev++ },
                        onNextPage = { next++ },
                        onChapter = {},
                        mode = mode,
                    )
                }
            }
        }
    }

    @Test
    fun `向左滑动翻下一页`() {
        render(ReadingMode.PAGED)
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, next)
        assertEquals(0, prev)
    }

    @Test
    fun `向右滑动翻上一页`() {
        render(ReadingMode.PAGED)
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(1, prev)
        assertEquals(0, next)
    }

    @Test
    fun `轻点左侧翻上一页`() {
        render(ReadingMode.PAGED)
        compose.onRoot().performTouchInput { click(Offset(width * 0.1f, height * 0.5f)) }
        compose.waitForIdle()
        assertEquals(1, prev)
        assertEquals(0, next)
    }

    @Test
    fun `轻点右侧翻下一页`() {
        render(ReadingMode.PAGED)
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        compose.waitForIdle()
        assertEquals(1, next)
        assertEquals(0, prev)
    }

    @Test
    fun `轻点中间不翻页`() {
        render(ReadingMode.PAGED)
        compose.onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        compose.waitForIdle()
        assertEquals(0, prev)
        assertEquals(0, next)
    }

    /**
     * 滚动模式下没有「翻页」这件事：左右滑不该触发任何翻页回调，
     * 否则用户左右会比划，页面就偷偷跳走了。
     */
    @Test
    fun `滚动模式下左右滑动不翻页`() {
        render(ReadingMode.SCROLL)
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(0, next)
        assertEquals(0, prev)
    }
}
