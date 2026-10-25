package io.github.galizhajidanbaofan.plainpage.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.ui.AppearanceState
import io.github.galizhajidanbaofan.plainpage.ui.ReadingMode
import io.github.galizhajidanbaofan.plainpage.ui.ShelfLayout
import io.github.galizhajidanbaofan.plainpage.ui.theme.ReadingTheme

/**
 * 外观设置。阶段 1 提供三组纯 UI 开关（阅读主题 / 书架布局 / 翻页方式），
 * 直接把状态写回 [onAppearanceChange]；阶段 2 会换成 ViewModel + DataStore 持久化，
 * 界面代码不需要改。
 */
@Composable
fun SettingsScreen(
    appearance: AppearanceState,
    onAppearanceChange: (AppearanceState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(12.dp))

            OptionRow(
                title = stringResource(R.string.settings_reading_theme),
                options = listOf(
                    ReadingTheme.LIGHT to stringResource(R.string.theme_light),
                    ReadingTheme.DARK to stringResource(R.string.theme_dark),
                    ReadingTheme.SEPIA to stringResource(R.string.theme_sepia),
                ),
                selected = appearance.theme,
                onSelected = { onAppearanceChange(appearance.copy(theme = it)) },
            )
            OptionRow(
                title = stringResource(R.string.settings_shelf_layout),
                options = listOf(
                    ShelfLayout.LIST to stringResource(R.string.layout_list),
                    ShelfLayout.GRID to stringResource(R.string.layout_grid),
                ),
                selected = appearance.shelfLayout,
                onSelected = { onAppearanceChange(appearance.copy(shelfLayout = it)) },
            )
            OptionRow(
                title = stringResource(R.string.settings_reading_mode),
                options = listOf(
                    ReadingMode.PAGED to stringResource(R.string.mode_paged),
                    ReadingMode.SCROLL to stringResource(R.string.mode_scroll),
                ),
                selected = appearance.readingMode,
                onSelected = { onAppearanceChange(appearance.copy(readingMode = it)) },
            )
        }
    }
}

@Composable
private fun <T> OptionRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                if (value == selected) {
                    FilledTonalButton(onClick = { onSelected(value) }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { onSelected(value) }) { Text(label) }
                }
            }
        }
    }
}
