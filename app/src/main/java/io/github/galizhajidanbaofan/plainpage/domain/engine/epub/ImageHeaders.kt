package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

/**
 * 只读图片头，拿宽高。
 *
 * 索引阶段要判断"这张图值不值得占一整屏"，靠文件体积判断很不准：实测某本合集里
 * 40×40 的装饰方块 JPEG 有 9 KB，比一些真插图还大。所以这里解析 PNG / JPEG / GIF / WebP
 * 的头部字段，拿到像素尺寸再判断，全程不申请位图内存。
 *
 * 解析不出来就返回 null，调用方据此保留该图——宁可多一页，也不能把真插图丢掉。
 */
internal object ImageHeaders {

    /** 返回 (宽, 高)；格式不认识或数据截断时返回 null。 */
    fun sizeOf(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 16) return null
        return when {
            isPng(bytes) -> png(bytes)
            isJpeg(bytes) -> jpeg(bytes)
            isGif(bytes) -> gif(bytes)
            isWebp(bytes) -> webp(bytes)
            else -> null
        }
    }

    private fun isPng(b: ByteArray): Boolean =
        b.size > 24 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte()

    private fun isJpeg(b: ByteArray): Boolean = b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()

    private fun isGif(b: ByteArray): Boolean =
        b.size > 10 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()

    private fun isWebp(b: ByteArray): Boolean =
        b.size > 30 && b[0] == 'R'.code.toByte() && b[8] == 'W'.code.toByte()

    /** IHDR 紧跟在 8 字节签名 + 4 字节长度 + 4 字节类型之后。 */
    private fun png(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 24) return null
        val width = readInt(b, 16)
        val height = readInt(b, 20)
        return if (width > 0 && height > 0) width to height else null
    }

    /** 顺着段标记找 SOFn，取里面的高度/宽度。 */
    private fun jpeg(b: ByteArray): Pair<Int, Int>? {
        var index = 2
        while (index + 9 < b.size) {
            if (b[index] != 0xFF.toByte()) {
                index++
                continue
            }
            val marker = b[index + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                index += 2
                continue
            }
            val length = ((b[index + 2].toInt() and 0xFF) shl 8) or (b[index + 3].toInt() and 0xFF)
            if (length < 2) return null
            val isSof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (isSof) {
                val height = ((b[index + 5].toInt() and 0xFF) shl 8) or (b[index + 6].toInt() and 0xFF)
                val width = ((b[index + 7].toInt() and 0xFF) shl 8) or (b[index + 8].toInt() and 0xFF)
                return if (width > 0 && height > 0) width to height else null
            }
            index += 2 + length
        }
        return null
    }

    private fun gif(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 10) return null
        val width = readShort(b, 6)
        val height = readShort(b, 8)
        return if (width > 0 && height > 0) width to height else null
    }

    private fun webp(b: ByteArray): Pair<Int, Int>? {
        // VP8X / VP8L / VP8 三种子格式的尺寸字段位置不同，这里只处理有损 VP8 与无损 VP8L。
        val chunk = String(b, 12, 4, Charsets.US_ASCII)
        return when (chunk) {
            "VP8 " -> {
                val width = readShort(b, 26) and 0x3FFF
                val height = readShort(b, 28) and 0x3FFF
                if (width > 0 && height > 0) width to height else null
            }

            "VP8L" -> {
                if (b.size < 25) return null
                val bits = (b[21].toInt() and 0xFF) or ((b[22].toInt() and 0xFF) shl 8) or
                    ((b[23].toInt() and 0xFF) shl 16) or ((b[24].toInt() and 0xFF) shl 24)
                val width = (bits and 0x3FFF) + 1
                val height = ((bits shr 14) and 0x3FFF) + 1
                width to height
            }

            else -> null
        }
    }

    private fun readInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or (b[offset + 3].toInt() and 0xFF)

    private fun readShort(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)
}
