package io.github.galizhajidanbaofan.plainpage.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.ui.reader.ReaderChapter
import io.github.galizhajidanbaofan.plainpage.ui.reader.ReaderContent
import io.github.galizhajidanbaofan.plainpage.ui.reader.ReaderUiState
import io.github.galizhajidanbaofan.plainpage.ui.theme.PlainPageTheme
import io.github.galizhajidanbaofan.plainpage.ui.theme.ThemeTokens
import io.github.galizhajidanbaofan.plainpage.ui.theme.ThemeTokensCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 阅读页离屏截图：喂一个假的 Ready 状态，验证正文排版、目录入口、翻页条的可见性。
 *
 * 注意：这里不验证真实文件的打开与进度写回（那是 ViewModel + Room 的事），
 * 只回答「界面长什么样、文字有没有被裁掉」。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ReaderScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val sampleText = buildString {
        appendLine("第一章 雪夜")
        appendLine()
        appendLine("那年冬天来得格外早。十月末就落了一场雪，把整条街压得安静下来，连挑担卖豆腐的都不肯出门。")
        appendLine("他坐在窗边，手里的书翻到第三十七页，却一个字也没读进去——楼上那户人家的收音机又在放同一段戏，反反复复，像谁在替他把时间按住。")
        appendLine()
        appendLine("书页的边角已经卷起，是被人反复摩挲过的那种卷。旧书摊的老陈说，这书之前有过主人，还是个爱做批注的。")
        appendLine("他果然在页边看到一行极淡的铅笔字：「此处可与前章参看」。字迹瘦，写得急，末尾一捺拖得很长，像是写字的人已经困了。")
        appendLine()
        appendLine("雪还在下。他把书合上又翻开，停在那一行批注上，忽然觉得这个陌生人隔着不知多少年，替他把这一句话读完了。")
    }

    private val chapters = listOf(
        ReaderChapter("第一章 雪夜", 0),
        ReaderChapter("第二章 旧书摊", 1_200),
        ReaderChapter("第三章 收音机", 2_400),
        ReaderChapter("第四章 那一行批注", 3_600),
    )

    private fun ready(lossy: Boolean = false) = ReaderUiState.Ready(
        title = "冬夜札记",
        chapterTitle = "第一章 雪夜",
        chapters = chapters,
        text = sampleText,
        pageIndex = 0,
        pageCount = 12,
        fraction = 0.0f,
        canPrev = false,
        canNext = true,
        lossyDecoding = lossy,
    )

    private fun render(state: ReaderUiState, tokens: ThemeTokens, file: String) {
        compose.setContent {
            PlainPageTheme(tokens = tokens) {
                ReaderContent(
                    state = state,
                    onBack = {},
                    onPrevPage = {},
                    onNextPage = {},
                    onChapter = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$file")
    }

    @Test
    fun readerPageLight() = render(ready(), ThemeTokensCatalog.Light, "06-reader-light.png")

    @Test
    fun readerPageSepia() = render(ready(), ThemeTokensCatalog.Sepia, "07-reader-sepia.png")

    @Test
    fun readerPageDark() = render(ready(), ThemeTokensCatalog.Dark, "08-reader-dark.png")

    @Test
    fun readerUnsupportedFormat() = render(
        ReaderUiState.Unsupported(BookFormat.PDF),
        ThemeTokensCatalog.Light,
        "09-reader-unsupported.png",
    )
}
