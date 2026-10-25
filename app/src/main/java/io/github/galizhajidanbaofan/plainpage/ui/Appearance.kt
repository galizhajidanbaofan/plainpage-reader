package io.github.galizhajidanbaofan.plainpage.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.galizhajidanbaofan.plainpage.ui.theme.ReadingTheme

/** 书架排布方式。业务层只读这个枚举，不关心网格用几列、列表卡片长什么样。 */
enum class ShelfLayout { LIST, GRID }

/** 阅读翻页方式。 */
enum class ReadingMode { PAGED, SCROLL }

/**
 * 全部可切换外观状态的快照。
 *
 * 阶段 1 先放在内存里；阶段 2 接 DataStore 后会由 `AppearanceRepository` 以 Flow 提供，
 * 这个 data class 保持不变，UI 层无需修改。
 */
data class AppearanceState(
    val theme: ReadingTheme = ReadingTheme.LIGHT,
    val shelfLayout: ShelfLayout = ShelfLayout.LIST,
    val readingMode: ReadingMode = ReadingMode.PAGED,
)

val LocalAppearance = staticCompositionLocalOf { AppearanceState() }

/** 让外观状态在进程被回收后仍能恢复（UI 切换不该因为旋转/后台而被重置）。 */
val AppearanceSaver: Saver<AppearanceState, Any> = listSaver(
    save = { listOf(it.theme.name, it.shelfLayout.name, it.readingMode.name) },
    restore = { saved ->
        AppearanceState(
            theme = ReadingTheme.valueOf(saved[0] as String),
            shelfLayout = ShelfLayout.valueOf(saved[1] as String),
            readingMode = ReadingMode.valueOf(saved[2] as String),
        )
    },
)
