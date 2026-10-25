package io.github.galizhajidanbaofan.plainpage.domain.engine.txt

import java.nio.ByteBuffer
import java.nio.CharBuffer

import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * TXT 可能出现的编码。故意只留这几项：
 * - UTF-8 是事实标准；
 * - UTF-16 只带 BOM 才认（没 BOM 的 UTF-16 在中文小说里几乎不出现，误判代价大于收益）；
 * - GB18030 是 GBK/GB2312 的超集，认它一个就覆盖了国内老书。
 *
 * 不引 juniversalchardet（MPL-1.1）：统计式探测在短文本上不如"先严格校验 UTF-8"可靠，
 * 而且这个判断是纯 ICU 无关逻辑，自己写比带一个依赖更好审。
 */
enum class TextEncoding(val charsetName: String, val label: String) {
    UTF8("UTF-8", "UTF-8"),
    UTF16LE("UTF-16LE", "UTF-16 LE"),
    UTF16BE("UTF-16BE", "UTF-16 BE"),
    GB18030("GB18030", "GB18030"),
    ;

    val charset: Charset get() = Charset.forName(charsetName)
}

/**
 * 编码识别。
 *
 * 判定顺序：BOM → 严格 UTF-8 → 严格 GB18030 → 兜底 GB18030（有损）。
 * 最后那档一定不会抛异常：宁可显示几个替换字符，也不能让用户打不开书。
 */
object TextEncodingDetector {

    /** 只读文件头这么多字节做判定；够用了，也避免为探测而读整个大文件。 */
    const val SAMPLE_BYTES: Int = 64 * 1024

    data class Result(
        val encoding: TextEncoding,
        /** BOM 占用字节数，解码时要从这个偏移往后读。 */
        val bomLength: Int,
        /** 严格校验全失败，只能有损解码。 */
        val lossy: Boolean,
    )

    fun detect(bytes: ByteArray): Result {
        bom(bytes)?.let { return it }

        // 无 BOM 的 UTF-16 会同时"像"别的编码，先按零字节分布排除。
        utf16WithoutBom(bytes)?.let { return Result(it, bomLength = 0, lossy = false) }

        if (isStrictlyDecodable(bytes, TextEncoding.UTF8.charset)) {
            return Result(TextEncoding.UTF8, bomLength = 0, lossy = false)
        }
        if (isStrictlyDecodable(bytes, TextEncoding.GB18030.charset)) {
            return Result(TextEncoding.GB18030, bomLength = 0, lossy = false)
        }
        return Result(TextEncoding.GB18030, bomLength = 0, lossy = true)
    }

    private fun bom(bytes: ByteArray): Result? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return Result(TextEncoding.UTF8, bomLength = 3, lossy = false)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Result(TextEncoding.UTF16LE, bomLength = 2, lossy = false)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Result(TextEncoding.UTF16BE, bomLength = 2, lossy = false)
        }
        return null
    }

    /**
     * 零字节在不同奇偶位置上扎堆，基本就是 UTF-16：ASCII 字符在高字节为 0。
     * 阈值取 30% 是经验值，纯 ASCII 文件不会误判（零字节比例接近 0）。
     */
    private fun utf16WithoutBom(bytes: ByteArray): TextEncoding? {
        val limit = minOf(bytes.size, 4096).let { if (it % 2 != 0) it - 1 else it }
        if (limit < 8) return null
        var evenZeros = 0
        var oddZeros = 0
        var i = 0
        while (i < limit) {
            if (bytes[i] == 0.toByte()) evenZeros++
            if (bytes[i + 1] == 0.toByte()) oddZeros++
            i += 2
        }
        val pairs = limit / 2
        return when {
            oddZeros * 10 > pairs * 3 -> TextEncoding.UTF16LE
            evenZeros * 10 > pairs * 3 -> TextEncoding.UTF16BE
            else -> null
        }
    }

    private fun isStrictlyDecodable(bytes: ByteArray, charset: Charset): Boolean {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        // 注意：三参数 decode() **不抛异常**，错误以 CoderResult.isError 返回（只有单参数版本才抛）。
        // endOfInput = false：采样样本可能在多字节字符中间被截断，那不算错误。
        val result = decoder.decode(
            ByteBuffer.wrap(bytes),
            CharBuffer.allocate(bytes.size + 16),
            false,
        )
        return !result.isError
    }
}
