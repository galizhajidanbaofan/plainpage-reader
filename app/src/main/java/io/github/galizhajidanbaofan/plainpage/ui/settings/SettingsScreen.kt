package io.github.galizhajidanbaofan.plainpage.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.ui.AppearanceState
import io.github.galizhajidanbaofan.plainpage.ui.ReadingFont
import io.github.galizhajidanbaofan.plainpage.ui.ReadingMode
import io.github.galizhajidanbaofan.plainpage.ui.ShelfLayout
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalTextStyles
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalThemeTokens
import io.github.galizhajidanbaofan.plainpage.ui.theme.ReadingFonts
import io.github.galizhajidanbaofan.plainpage.ui.theme.ReadingTheme

/**
 * 外观设置。四组纯 UI 开关（阅读主题 / 书架布局 / 翻页方式），直接把状态写回
 * [onAppearanceChange]；持久化在 Repository 那一层，界面代码不需要改。
 *
 * 选择器做成「字 + 下划线」而不是按钮：纸面上没有凸起的按钮，只有被划出来的一行字。
 */
@Composable
fun SettingsScreen(
    appearance: AppearanceState,
    onAppearanceChange: (AppearanceState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Surface(color = tokens.paper, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.settings_kicker),
                style = styles.kicker,
                color = tokens.textSecondary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = tokens.divider, thickness = 1.dp)

            OptionSection(
                title = stringResource(R.string.settings_reading_theme),
                options = listOf(
                    ReadingTheme.LIGHT to stringResource(R.string.theme_light),
                    ReadingTheme.DARK to stringResource(R.string.theme_dark),
                    ReadingTheme.SEPIA to stringResource(R.string.theme_sepia),
                ),
                selected = appearance.theme,
                onSelected = { onAppearanceChange(appearance.copy(theme = it)) },
            )
            OptionSection(
                title = stringResource(R.string.settings_shelf_layout),
                options = listOf(
                    ShelfLayout.LIST to stringResource(R.string.layout_list),
                    ShelfLayout.GRID to stringResource(R.string.layout_grid),
                ),
                selected = appearance.shelfLayout,
                onSelected = { onAppearanceChange(appearance.copy(shelfLayout = it)) },
            )
            OptionSection(
                title = stringResource(R.string.settings_reading_mode),
                options = listOf(
                    ReadingMode.PAGED to stringResource(R.string.mode_paged),
                    ReadingMode.SCROLL to stringResource(R.string.mode_scroll),
                ),
                selected = appearance.readingMode,
                onSelected = { onAppearanceChange(appearance.copy(readingMode = it)) },
            )
            // 字体这一组把每个选项名用该字体本身渲染，选之前就能看出差别；
            // 共五个选项，用 FlowRow 让窄屏自动换行，而不是硬挤在一行里。
            OptionSection(
                title = stringResource(R.string.settings_font),
                options = listOf(
                    ReadingFont.SYSTEM to stringResource(R.string.font_system),
                    ReadingFont.SONG to stringResource(R.string.font_song),
                    ReadingFont.KAI to stringResource(R.string.font_kai),
                    ReadingFont.DENG to stringResource(R.string.font_deng),
                    ReadingFont.TYPEWRITER to stringResource(R.string.font_typewriter),
                ),
                selected = appearance.font,
                onSelected = { onAppearanceChange(appearance.copy(font = it)) },
                labelStyle = { font ->
                    ReadingFonts.apply(styles.screenTitle.copy(fontSize = 20.sp), font)
                },
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionSection(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    labelStyle: ((T) -> TextStyle)? = null,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
        Text(
            text = title,
            style = styles.meta,
            color = tokens.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Column(
                    modifier = Modifier.clickable { onSelected(value) },
                ) {
                    Text(
                        text = label,
                        style = labelStyle?.invoke(value) ?: styles.screenTitle,
                        color = if (isSelected) tokens.accent else tokens.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(if (isSelected) tokens.accent else Color.Transparent),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = tokens.divider, thickness = 1.dp)
    }
}
