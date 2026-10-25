package io.github.galizhajidanbaofan.plainpage.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.galizhajidanbaofan.plainpage.ui.AppearanceState
import io.github.galizhajidanbaofan.plainpage.ui.ReadingMode
import io.github.galizhajidanbaofan.plainpage.ui.ShelfLayout
import io.github.galizhajidanbaofan.plainpage.ui.settings.SettingsScreen
import io.github.galizhajidanbaofan.plainpage.ui.theme.PlainPageTheme
import io.github.galizhajidanbaofan.plainpage.ui.theme.ReadingTheme
import io.github.galizhajidanbaofan.plainpage.ui.theme.ThemeTokens
import io.github.galizhajidanbaofan.plainpage.ui.theme.ThemeTokensCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 设置页离屏截图：确认三组开关在三种主题下的选中态、分隔线与字距。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(tokens: ThemeTokens, file: String) {
        compose.setContent {
            PlainPageTheme(tokens = tokens) {
                SettingsScreen(
                    appearance = AppearanceState(
                        theme = ReadingTheme.LIGHT,
                        shelfLayout = ShelfLayout.LIST,
                        readingMode = ReadingMode.PAGED,
                    ),
                    onAppearanceChange = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$file")
    }

    @Test
    fun settingsLight() = render(ThemeTokensCatalog.Light, "10-settings-light.png")

    @Test
    fun settingsDark() = render(ThemeTokensCatalog.Dark, "11-settings-dark.png")
}
