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
 * 正文字体。前三种是中文阅读的实际需求（默认黑体 / 宋体 / 楷体），
 * 第四种（等线细体）偏西文排版观感。
 *
 * 楷体是系统里没有的，随包带了一款开源楷体（LXGW WenKai Lite，OFL-1.1）；
 * 其余几种用系统内置字族，不额外增加包体。
 */
enum class ReadingFont { SYSTEM, SONG, KAI, DENG }

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
    val font: ReadingFont = ReadingFont.SYSTEM,
)

val LocalAppearance = staticCompositionLocalOf { AppearanceState() }

/** 让外观状态在进程被回收后仍能恢复（UI 切换不该因为旋转/后台而被重置）。 */
val AppearanceSaver: Saver<AppearanceState, Any> = listSaver(
    save = { listOf(it.theme.name, it.shelfLayout.name, it.readingMode.name, it.font.name) },
    restore = { saved ->
        // 用 getOrNull 而不是下标：以后加字段时，旧进程留下的存档也能救回来，不必崩一次。
        AppearanceState(
            theme = saved.getOrNull(0)?.toString()?.let { runCatching { ReadingTheme.valueOf(it) }.getOrNull() }
                ?: ReadingTheme.LIGHT,
            shelfLayout = saved.getOrNull(1)?.toString()?.let { runCatching { ShelfLayout.valueOf(it) }.getOrNull() }
                ?: ShelfLayout.LIST,
            readingMode = saved.getOrNull(2)?.toString()?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
                ?: ReadingMode.PAGED,
            font = saved.getOrNull(3)?.toString()?.let { runCatching { ReadingFont.valueOf(it) }.getOrNull() }
                ?: ReadingFont.SYSTEM,
        )
    },
)
