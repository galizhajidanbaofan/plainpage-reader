package io.github.galizhajidanbaofan.plainpage.domain.engine.pdf

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 位图像素尺寸。放在 domain 里而不是直接用 Compose 的 IntSize，免得领域层依赖 UI 框架。 */
data class PagePixelSize(val width: Int, val height: Int)

/**
 * PDF 的页码算术。抽成纯函数是因为 [PdfReaderEngine] 依赖 Android 的 `PdfRenderer`，
 * 在 JVM 单测里跑不起来——把所有"能算错的地方"搬到这里，引擎只留胶水。
 */
object PdfPageMath {

    /** 渲染宽度的下限：再窄就只剩马赛克，不如不画。 */
    const val MIN_RENDER_WIDTH_PX = 360

    /** 渲染宽度上限：超过它也不会更清楚，只会吃内存。 */
    const val MAX_RENDER_WIDTH_PX = 2_400

    /**
     * 单页位图像素上限（约 4 MP ≈ 16 MB ARGB_8888）。
     * 横版海报式的 PDF 页按宽度渲染会得到很高的位图，这里兜一次底。
     */
    const val MAX_BITMAP_PIXELS = 4_000_000

    /** 页码夹紧：越界时落到有效范围内，空书返回 0。 */
    fun clampPage(index: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return index.coerceIn(0, pageCount - 1)
    }

    /**
     * 页码 → 全书百分比。单页文件返回 1f（已经读完了），
     * 多页时首页 0f、末页 1f，让书架上的进度条首尾都说得通。
     */
    fun fractionOf(pageIndex: Int, pageCount: Int): Float {
        if (pageCount <= 1) return if (pageCount == 1) 1f else 0f
        return clampPage(pageIndex, pageCount).toFloat() / (pageCount - 1)
    }

    /** 百分比 → 页码（四舍五入，别让 0.99 停在前一页）。 */
    fun pageOf(fraction: Float, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        val clamped = fraction.coerceIn(0f, 1f)
        return clampPage((clamped * (pageCount - 1)).roundToInt(), pageCount)
    }

    /**
     * 由 PDF 页的点尺寸（72 dpi）与目标显示宽度算出位图尺寸。
     *
     * - 宽度先夹到 [MIN_RENDER_WIDTH_PX]..[MAX_RENDER_WIDTH_PX]；
     * - 高度按原始宽高比换算；
     * - 若总像素超过 [MAX_BITMAP_PIXELS]，按面积等比缩回去（宁可略糊，也不 OOM）；
     * - 有任何非法输入都退到 1:1 的方页，绝不让调用方拿到 0 宽高。
     */
    fun bitmapSize(pageWidth: Int, pageHeight: Int, targetWidthPx: Int): PagePixelSize {
        if (pageWidth <= 0 || pageHeight <= 0) {
            val side = targetWidthPx.coerceIn(MIN_RENDER_WIDTH_PX, MAX_RENDER_WIDTH_PX)
            return PagePixelSize(side, side)
        }
        val width = targetWidthPx.coerceIn(MIN_RENDER_WIDTH_PX, MAX_RENDER_WIDTH_PX)
        val height = (width.toFloat() * pageHeight / pageWidth).roundToInt().coerceAtLeast(1)
        val pixels = width.toLong() * height
        if (pixels > MAX_BITMAP_PIXELS) {
            val scale = sqrt(MAX_BITMAP_PIXELS.toDouble() / pixels).toFloat()
            return PagePixelSize(
                width = (width * scale).toInt().coerceAtLeast(MIN_RENDER_WIDTH_PX),
                height = (height * scale).toInt().coerceAtLeast(1),
            )
        }
        return PagePixelSize(width, height)
    }
}
