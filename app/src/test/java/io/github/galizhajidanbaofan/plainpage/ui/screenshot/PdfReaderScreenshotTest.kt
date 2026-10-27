package io.github.galizhajidanbaofan.plainpage.ui.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.galizhajidanbaofan.plainpage.ui.AppearanceState
import io.github.galizhajidanbaofan.plainpage.ui.LocalAppearance
import io.github.galizhajidanbaofan.plainpage.ui.ReadingMode
import io.github.galizhajidanbaofan.plainpage.ui.reader.pdf.PdfReaderContent
import io.github.galizhajidanbaofan.plainpage.ui.reader.pdf.PdfReaderUiState
import io.github.galizhajidanbaofan.plainpage.ui.theme.PlainPageTheme
import io.github.galizhajidanbaofan.plainpage.ui.theme.ThemeTokensCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PDF 阅读页的离屏截图。
 *
 * 真正的 `PdfRenderer` 只能在设备/模拟器上跑，JVM 里没有实现；所以这里用一张自绘的
 * "看起来像 PDF 页"的位图喂给界面，验证版式（页脚进度线、页眉、留白、按页缩放）而不是解码。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PdfReaderScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** 一张 A4 比例的"书页"：白底、标题、若干行正文横线，用来验证位图确实被等比塞进版面。 */
    private fun fakePage(width: Int = 900, height: Int = 1273): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val ink = Paint().apply { color = Color.rgb(0x2B, 0x2B, 0x2B) }
        val faint = Paint().apply { color = Color.rgb(0xC9, 0xC6, 0xBC) }
        val title = Paint().apply {
            color = Color.rgb(0x1F, 0x1F, 0x1F)
            textSize = 34f
            isAntiAlias = true
        }
        val small = Paint().apply {
            color = Color.rgb(0x6A, 0x68, 0x60)
            textSize = 22f
            isAntiAlias = true
        }

        canvas.drawText("第三章  河岸", 72f, 120f, title)
        canvas.drawText("PLAINPAGE / PDF RENDERER", 72f, 156f, small)

        // 正文：用条块代替文字，避免依赖字体，只验证版式
        var y = 210f
        repeat(16) { line ->
            val isParagraphEnd = line % 4 == 3
            val width = if (isParagraphEnd) width * 0.45f else width * 0.86f
            canvas.drawRoundRect(72f, y, 72f + width, y + 18f, 6f, 6f, ink)
            y += if (isParagraphEnd) 62f else 42f
        }

        canvas.drawLine(72f, height - 90f, width - 72f, height - 90f, faint)
        canvas.drawText("— 42 —", width / 2f - 30f, height - 52f, small)
        return bitmap
    }

    private fun render(
        state: PdfReaderUiState,
        file: String,
        mode: ReadingMode = ReadingMode.PAGED,
        scrollFraction: Float = 0f,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides AppearanceState(readingMode = mode)) {
                PlainPageTheme(tokens = ThemeTokensCatalog.Light) {
                    PdfReaderContent(
                        state = state,
                        onBack = {},
                        onPrevPage = {},
                        onNextPage = {},
                        onGoToPage = {},
                        onViewportWidth = {},
                        loadPage = { _, _ -> pageBitmap },
                        onScrollPage = {},
                        onFlushPage = {},
                        mode = mode,
                        scrollFraction = scrollFraction,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$file")
    }

    private val pageBitmap by lazy { fakePage().asImageBitmap() }

    private fun ready(pageIndex: Int = 2, pageCount: Int = 42) = PdfReaderUiState.Ready(
        title = "沿河而下",
        pageIndex = pageIndex,
        pageCount = pageCount,
        fraction = pageIndex / (pageCount - 1).toFloat(),
        image = pageBitmap,
        aspect = 900f / 1273f,
    )

    @Test
    fun pdfPagedLight() = render(ready(), "16-reader-pdf-light.png")

    @Test
    fun pdfScrollLight() = render(
        // 滚动模式从第一页开始看，页眉也就该说"第 1 页"
        state = ready(pageIndex = 0),
        file = "17-reader-pdf-scroll.png",
        mode = ReadingMode.SCROLL,
        scrollFraction = 0.05f,
    )
}
