package io.github.galizhajidanbaofan.plainpage.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import io.github.galizhajidanbaofan.plainpage.ui.ShelfLayout
import io.github.galizhajidanbaofan.plainpage.ui.shelf.ShelfScreen
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
 * 离屏截图：在 JVM 上用 Robolectric 的原生图形把 Compose 界面真的画成 PNG。
 *
 * 为什么需要它：本机跑不起 Android 模拟器（宿主虚拟化/图形都不满足），但又必须能
 * 「看见」界面长什么样。这套测试不启动模拟器、不联网，`./gradlew :app:testDebugUnitTest`
 * 就会把图写进 `app/build/screenshots/`，可直接当验收物看。
 *
 * 注意：这里只验证**渲染与布局**；点击、返回键、SAF 选择器、Room 落库等行为仍需设备验证。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ShelfScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    // 用真实当前时间，这样「最近阅读」列显示的是 2 小时前/2 天前这类相对时间。
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L
    private val day = 24 * hour

    private val books = listOf(
        Book(
            id = 1,
            title = "红楼梦",
            author = "曹雪芹",
            format = BookFormat.EPUB,
            localPath = "/data/books/1.epub",
            sourceUri = "content://docs/1",
            byteSize = 2_300_000L,
            importedAt = now - 30 * day,
            lastOpenedAt = now - 2 * hour,
        ),
        Book(
            id = 2,
            title = "围城",
            author = "钱锺书",
            format = BookFormat.TXT,
            localPath = "/data/books/2.txt",
            sourceUri = "content://docs/2",
            byteSize = 780_000L,
            importedAt = now - 20 * day,
            lastOpenedAt = now - 2 * day,
            readingLocation = ReadingLocation.Txt(charOffset = 320_000, chapterIndex = 7, fraction = 0.42f),
        ),
        Book(
            id = 3,
            title = "深度学习",
            format = BookFormat.PDF,
            localPath = "/data/books/3.pdf",
            sourceUri = "content://docs/3",
            byteSize = 52_000_000L,
            importedAt = now - 10 * day,
            lastOpenedAt = now - 9 * day,
        ),
        Book(
            id = 4,
            title = "一个特别特别长的书名用来检查列表里的省略号是否会因为名字太长而把整行挤坏",
            author = "佚名",
            format = BookFormat.TXT,
            localPath = "/data/books/4.txt",
            sourceUri = "content://docs/4",
            byteSize = 120_000L,
            importedAt = now - 3 * day,
            lastOpenedAt = null,
            readingLocation = ReadingLocation.Txt(charOffset = 4_000, chapterIndex = 1, fraction = 0.06f),
        ),
    )

    private fun render(
        layout: ShelfLayout,
        tokens: ThemeTokens,
        sample: List<Book>,
        file: String,
    ) {
        compose.setContent {
            PlainPageTheme(tokens = tokens) {
                ShelfScreen(
                    layout = layout,
                    books = sample,
                    importing = false,
                    onImport = {},
                    onOpen = {},
                    onDelete = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$file")
    }

    @Test
    fun shelfListLight() = render(ShelfLayout.LIST, ThemeTokensCatalog.Light, books, "01-shelf-list-light.png")

    @Test
    fun shelfGridLight() = render(ShelfLayout.GRID, ThemeTokensCatalog.Light, books, "02-shelf-grid-light.png")

    @Test
    fun shelfListDark() = render(ShelfLayout.LIST, ThemeTokensCatalog.Dark, books, "03-shelf-list-dark.png")

    @Test
    fun shelfGridSepia() = render(ShelfLayout.GRID, ThemeTokensCatalog.Sepia, books, "04-shelf-grid-sepia.png")

    @Test
    fun shelfEmpty() = render(ShelfLayout.LIST, ThemeTokensCatalog.Light, emptyList(), "05-shelf-empty.png")
}
