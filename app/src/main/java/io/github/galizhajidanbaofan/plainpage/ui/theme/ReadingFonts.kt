package io.github.galizhajidanbaofan.plainpage.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.ui.ReadingFont

/**
 * 正文字体到 Compose [FontFamily] 的映射。业务层只认 [ReadingFont] 枚举，
 * 不关心某个字体是系统字族还是随包带的文件。
 *
 * 取舍：
 * - 默认 / 宋体 / 等线 / 打字机 用系统字族，零包体成本；
 * - 楷体在 Android 上没有系统字族（`cursive` 在中文上会回退到黑体，等于没换），
 *   所以随包带 `LXGW WenKai Lite`（OFL-1.1，9.75 MB）。带上后 APK 会明显变大，
 *   这是「真·楷体」与「假楷体」之间的代价，选前者。
 * - 等线用系统无衬线的 Light 字重近似：Android 没有等线，而细黑是无衬线里最接近的观感。
 */
object ReadingFonts {

    private val kai: FontFamily = FontFamily(Font(R.font.lxgw_wenkai_lite))

    fun family(font: ReadingFont): FontFamily = when (font) {
        ReadingFont.SYSTEM -> FontFamily.SansSerif
        ReadingFont.SONG -> FontFamily.Serif
        ReadingFont.KAI -> kai
        ReadingFont.DENG -> FontFamily.SansSerif
        ReadingFont.TYPEWRITER -> FontFamily.Monospace
    }

    fun weight(font: ReadingFont): FontWeight = when (font) {
        ReadingFont.DENG -> FontWeight.Light
        else -> FontWeight.Normal
    }

    /**
     * 把字体应用到正文样式上。
     *
     * 等宽与细黑的行高感受不一样：等宽字面更宽、行距显松，细黑笔画更轻、需要略小一点字距，
     * 所以这里不原样照抄，而是给这两种字体各调一点，别让「换字体」变成「排版错位」。
     */
    fun apply(base: TextStyle, font: ReadingFont): TextStyle = when (font) {
        ReadingFont.TYPEWRITER -> base.copy(
            fontFamily = family(font),
            fontWeight = weight(font),
            letterSpacing = 0.sp,
        )

        ReadingFont.DENG -> base.copy(
            fontFamily = family(font),
            fontWeight = weight(font),
            letterSpacing = 0.6.sp,
        )

        else -> base.copy(fontFamily = family(font), fontWeight = weight(font))
    }
}
