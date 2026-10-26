package io.github.galizhajidanbaofan.plainpage.ui.screenshot

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import androidx.compose.runtime.CompositionLocalProvider
import io.github.galizhajidanbaofan.plainpage.ui.AppearanceState
import io.github.galizhajidanbaofan.plainpage.ui.LocalAppearance
import io.github.galizhajidanbaofan.plainpage.ui.ReadingMode
import io.github.galizhajidanbaofan.plainpage.ui.reader.ScrollChunkMath
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
        charCount = sampleText.length,
        anchorChar = 0,
    )

    private fun render(
        state: ReaderUiState,
        tokens: ThemeTokens,
        file: String,
        mode: ReadingMode = ReadingMode.PAGED,
        scrollFraction: Float = 0f,
        chunk: (Int) -> String = { index ->
            val start = ScrollChunkMath.charOffsetOf(index)
            sampleText.substring(start.coerceIn(0, sampleText.length))
        },
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides AppearanceState(readingMode = mode)) {
                PlainPageTheme(tokens = tokens) {
                    ReaderContent(
                        state = state,
                        onBack = {},
                        onPrevPage = {},
                        onNextPage = {},
                        onChapter = {},
                        mode = mode,
                        scrollFraction = scrollFraction,
                        loadChunk = { index -> chunk(index) },
                    )
                }
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

    /**
     * 滚动模式：没有「上一页/下一页」，只有连续下滚；同一份状态换个模式就该换一套骨架。
     * 这张图同时证明进度线仍在最底下（2dp，按 charOffset 换算成 37.5%），只是不再有页数文字。
     *
     * 为了和翻页截图区分开，这里刻意让锚点落在书中段（第 7 块），块内文字带块序号。
     */
    @Test
    fun readerScrollMode() = render(
        state = ready().copy(charCount = 24_000, anchorChar = 9_000, fraction = 0.375f),
        tokens = ThemeTokensCatalog.Light,
        file = "12-reader-scroll.png",
        mode = ReadingMode.SCROLL,
        scrollFraction = 0.375f,
        chunk = { index -> "第 ${index + 1} 块\n\n$sampleText" },
    )

    /**
     * 整页插图：EPUB 里占满一屏，等比例缩放、上下留白居中，图注在图下。
     * 用一张手绘的示意图而不是真照片，免得仓库里多出个来源不明的二进制。
     */
    @Test
    fun readerPicturePage() {
        val width = 900
        val height = 1200
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(0xFF6E7F76.toInt())
        val paint = android.graphics.Paint().apply { color = 0xFFEFF1EC.toInt() }
        for (i in 0 until 7) {
            val y = height * (i + 1) / 8f
            canvas.drawRect(width * 0.12f, y, width * 0.88f, y + 6f, paint)
        }
        render(
            state = ready().copy(text = "", picture = bitmap.asImageBitmap(), pictureCaption = "插图：1978 年的雪夜（示意图）"),
            tokens = ThemeTokensCatalog.Light,
            file = "13-reader-picture.png",
        )
    }
}
