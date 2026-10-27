package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import java.net.URLDecoder

/**
 * EPUB 解析期的可预期失败。只在引擎内部流转，对外由 `ReaderException` 翻译成用户看得懂的原因。
 */
internal class EpubFormatException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        /** zip 打不开，或 container.xml / OPF / spine 缺失、不合规。 */
        CORRUPTED,

        /** spine 里没有一段能取出正文的资源。 */
        EMPTY_CONTENT,

        /** 单个章节或全书正文超过上限。 */
        TOO_LARGE,
    }
}

/**
 * 解析上限。数值的选择依据：
 * - 单章 8 MiB：正常一章 XHTML 几十 KB，8 MiB 已经能容纳整本书塞进一个文件的情况；
 * - 全书 800 万字符（约 16 MB 的 UTF-16 内存占用 + 一个 String 的开销）：
 *   100 万字级的中文小说绰绰有余，同时挡住"解压炸弹"把内存吃穿。
 */
internal object EpubLimits {
    const val MAX_XML_BYTES: Int = 4 * 1024 * 1024
    const val MAX_CHAPTER_BYTES: Int = 8 * 1024 * 1024
    const val MAX_TOTAL_CHARS: Int = 8_000_000
    const val MAX_SPINE_ITEMS: Int = 2_000

    /** 单张插图解压上限：超过就当作畸形文件跳过，不把内存交给它。 */
    const val MAX_PICTURE_BYTES: Int = 8 * 1024 * 1024

    /** 小于这个体积的图基本是项目符号、分隔线、装饰角花，不值得占一页。 */
    const val MIN_PICTURE_BYTES: Long = 6 * 1024

    /**
     * 宽或高小于这个像素数的图按装饰处理。
     *
     * 只靠体积判断会漏：40×40 的装饰方块 JPEG 可以比真插图还大（实测 9 KB）。
     * 真插图（哪怕是小尺寸的章节头图）通常也在 200px 以上。
     */
    const val MIN_PICTURE_EDGE: Int = 96

    /** 读图片头用的字节数：PNG 只要 24 字节，JPEG 要往后翻到 SOFn，64 KB 足够。 */
    const val MAX_PICTURE_HEADER_BYTES: Int = 64 * 1024

    /** 全书最多认这么多张插图；再多就不再记（避免漫画类 EPUB 把锚点表撑爆）。 */
    const val MAX_PICTURES: Int = 600
}

/**
 * EPUB 内部路径处理。
 *
 * 必须自己归一化而不是直接 `zip.getEntry(href)`：
 * - href 是相对 OPF 所在目录的，且可能带 `%20` 之类的百分号编码；
 * - href 可能带 `#fragment`（目录跳转常这么写）；
 * - 最要紧的是安全：`../../` 这类条目要么是畸形文件，要么是刻意的路径穿越，
 *   一律按"解析不出来"处理，绝不允许跳出压缩包根。
 */
internal object ZipPaths {

    /** 归一化绝对路径；越界（`..` 弹出空栈）返回 null。 */
    fun normalize(raw: String): String? {
        val stack = ArrayList<String>()
        for (segment in raw.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
                else -> stack.add(segment)
            }
        }
        return if (stack.isEmpty()) null else stack.joinToString("/")
    }

    /** 把 [href] 解析成相对压缩包根的路径。 */
    fun resolve(baseDir: String, href: String): String? {
        val target = href.substringBefore('#').trim()
        if (target.isEmpty()) return null
        // 百分号编码在真实 EPUB 里很常见（中文文件名尤其多），解不开就按原样处理。
        val decoded = runCatching { URLDecoder.decode(target, "UTF-8") }.getOrDefault(target)
        val combined = if (decoded.startsWith('/')) decoded else baseDir.trim('/') + "/" + decoded
        return normalize(combined)
    }

    /** 目录部分；没有目录时返回空串。 */
    fun directoryOf(path: String): String = path.substringBeforeLast('/', "")

    /** 文件名去扩展名，用作没有目录时的兜底章节名。 */
    fun stemOf(path: String): String =
        path.substringAfterLast('/').substringBeforeLast('.').ifBlank { path.substringAfterLast('/') }
}
