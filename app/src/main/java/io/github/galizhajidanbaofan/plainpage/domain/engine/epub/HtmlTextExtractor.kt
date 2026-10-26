package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

/**
 * 提取结果：[text] 是正文纯文本，[images] 按出现位置排好序，[ExtractedImage.offset] 是
 * 该插图在 [text] 中的字符位置（指向"图片本该出现在两个字之间"的那个间隙）。
 */
internal data class ExtractedHtml(
    val text: String,
    val images: List<ExtractedImage>,
)

/** 一处插图引用。此时 [src] 还是相对当前章的原始 href，由调用方解析成压缩包内路径。 */
internal data class ExtractedImage(
    val src: String,
    val alt: String?,
    val offset: Int,
)

/**
 * XHTML → 纯文本。
 *
 * 为什么自研而不用 jsoup：这里的需求只有一个「把标签去掉、把段落分开」，
 * 不需要 CSS 选择器、DOM 树和 HTML5 容错解析器带来的体量；而正文提取是纯字符扫描，
 * 单测里跑得飞快，也不会在畸形文档上抛异常。
 *
 * 不做的事（P0 明确不做）：保留加粗/斜体/脚注链接等行内样式、表格排版。
 * 表格按行输出，够读；样式交给阅读页统一排版。
 *
 * 图片只记"在哪"与"指向谁"，不在这里解码：解码属于渲染阶段的活，
 * 而且同一张图在目录里可能被引用多次，放在这里会重复解码。
 */
internal object HtmlTextExtractor {

    private val SKIP_TAGS = setOf("script", "style", "head", "title", "meta", "link", "svg", "iframe", "object")

    private val BLOCK_TAGS = setOf(
        "p", "div", "br", "li", "ul", "ol", "dl", "dt", "dd", "tr", "table", "thead", "tbody",
        "section", "article", "aside", "header", "footer", "main", "nav", "blockquote", "pre",
        "h1", "h2", "h3", "h4", "h5", "h6", "hr", "figure", "figcaption",
    )

    /** 图片占位符：用一个私用区字符在扫描时标出位置，归一化之后再换成字符偏移。 */
    private const val IMAGE_MARK: Char = 0xE000.toChar()

    fun extract(markup: String): ExtractedHtml {
        val images = ArrayList<ExtractedImage>()
        val out = StringBuilder(markup.length / 2)
        var index = 0
        var skipDepth = 0

        fun breakLine() {
            // 去掉行尾空格，再补一个换行；已换行则不重复。
            while (out.isNotEmpty() && (out.last() == ' ' || out.last() == '\t')) {
                out.setLength(out.length - 1)
            }
            // 占位符本身代表已经换过行，别再补一个，否则插图两侧会多出空行。
            if (out.isNotEmpty() && out.last() != '\n' && out.last() != IMAGE_MARK) out.append('\n')
        }

        while (index < markup.length) {
            val char = markup[index]

            if (char == '<') {
                if (markup.startsWith("<!--", index)) {
                    val end = markup.indexOf("-->", index)
                    index = if (end < 0) markup.length else end + 3
                    continue
                }
                val end = markup.indexOf('>', index)
                if (end < 0) break
                val rawTag = markup.substring(index + 1, end).trim()
                index = end + 1
                if (rawTag.isEmpty()) continue

                val closing = rawTag.startsWith('/')
                val selfClosing = rawTag.endsWith('/')
                val name = rawTag
                    .removePrefix("/")
                    .trim()
                    .substringBefore(' ')
                    .substringBefore('/')
                    .substringAfter(':')      // svg:script / html:br 之类的怪写法
                    .lowercase()

                if (name in SKIP_TAGS) {
                    if (closing) {
                        skipDepth = (skipDepth - 1).coerceAtLeast(0)
                    } else if (!selfClosing) {
                        skipDepth++
                    }
                    continue
                }
                if (skipDepth > 0) continue
                if (name == "img" || name == "image") {
                    val src = attribute(rawTag, "src") ?: attribute(rawTag, "href")
                    if (!src.isNullOrBlank() && !src.startsWith("data:") && !src.startsWith("javascript:")) {
                        images.add(ExtractedImage(src = src, alt = attribute(rawTag, "alt"), offset = -1))
                        out.append(IMAGE_MARK)
                    }
                    continue
                }
                if (name in BLOCK_TAGS) breakLine()
                continue
            }

            if (skipDepth > 0) {
                index++
                continue
            }

            when {
                char == '\n' || char == '\r' || char == '\t' || char == ' ' -> {
                    if (out.isNotEmpty() && out.last() != '\n' && out.last() != ' ') out.append(' ')
                    index++
                }

                char == '&' -> {
                    val semicolon = markup.indexOf(';', index)
                    if (semicolon in (index + 1)..(index + 10)) {
                        out.append(decodeEntity(markup.substring(index + 1, semicolon)))
                        index = semicolon + 1
                    } else {
                        out.append(char)
                        index++
                    }
                }

                else -> {
                    out.append(char)
                    index++
                }
            }
        }
        return normalizeWithImages(normalize(out), images)
    }

    /** 把归一化后的正文里的占位符摘掉，同时把每张图的偏移定下来。 */
    private fun normalizeWithImages(normalized: String, images: List<ExtractedImage>): ExtractedHtml {
        if (images.isEmpty()) return ExtractedHtml(normalized, emptyList())
        val text = StringBuilder(normalized.length)
        val placed = ArrayList<ExtractedImage>(images.size)
        var next = 0
        for (char in normalized) {
            if (char == IMAGE_MARK) {
                // 占位符个数与图片条目数一致（只有 img 标签会写它），对不上就按顺序能配几对配几对。
                val source = images.getOrNull(next) ?: continue
                placed.add(source.copy(offset = text.length))
                next++
                continue
            }
            text.append(char)
        }
        return ExtractedHtml(text.toString(), placed)
    }

    /** 极简属性解析：只认 `name="value"` / `name='value'`，够取 src 与 alt。 */
    private fun attribute(rawTag: String, name: String): String? {
        var index = 0
        while (true) {
            index = rawTag.indexOf(name, index, ignoreCase = true)
            if (index < 0) return null
            // 必须是完整的属性名起点（前一个字符是空白），且后面紧跟 `=`
            val beforeOk = index == 0 || rawTag[index - 1].isWhitespace()
            var cursor = index + name.length
            while (cursor < rawTag.length && rawTag[cursor].isWhitespace()) cursor++
            if (beforeOk && cursor < rawTag.length && rawTag[cursor] == '=') {
                cursor++
                while (cursor < rawTag.length && rawTag[cursor].isWhitespace()) cursor++
                if (cursor < rawTag.length) {
                    val quote = rawTag[cursor]
                    if (quote.code == 0x22 || quote.code == 0x27) {
                        val close = rawTag.indexOf(quote, cursor + 1)
                        if (close > cursor) return rawTag.substring(cursor + 1, close)
                    } else {
                        val end = rawTag.indexOfAny(charArrayOf(' ', '	', '/', '>'), cursor)
                        return rawTag.substring(cursor, if (end < 0) rawTag.length else end)
                    }
                }
            }
            index += name.length
        }
    }

    /** 逐行去空白、丢掉空行，保证段落之间恰好一个换行。 */
    private fun normalize(raw: CharSequence): String {
        val result = StringBuilder(raw.length)
        var pendingLine = StringBuilder()
        fun flush() {
            val line = pendingLine.toString().trim()
            pendingLine = StringBuilder()
            if (line.isEmpty()) return
            if (result.isNotEmpty()) result.append('\n')
            result.append(line)
        }
        for (char in raw) {
            if (char == '\n') flush() else pendingLine.append(char)
        }
        flush()
        return result.toString()
    }

    private fun decodeEntity(name: String): String = when {
        name.startsWith("#x") || name.startsWith("#X") ->
            codePointToString(name.drop(2).toIntOrNull(16))

        name.startsWith("#") -> codePointToString(name.drop(1).toIntOrNull(10))

        else -> NAMED_ENTITIES[name] ?: "&$name;"
    }

    private fun codePointToString(codePoint: Int?): String {
        if (codePoint == null || codePoint <= 0 || codePoint > 0x10FFFF) return ""
        return String(Character.toChars(codePoint))
    }

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "mdash" to "—",
        "ndash" to "–",
        "hellip" to "…",
        "ldquo" to "“",
        "rdquo" to "”",
        "lsquo" to "‘",
        "rsquo" to "’",
        "middot" to "·",
        "bull" to "•",
        "copy" to "©",
        "reg" to "®",
        "times" to "×",
        "laquo" to "«",
        "raquo" to "»",
        "ensp" to " ",
        "emsp" to " ",
        "thinsp" to " ",
        "shy" to "",
    )
}
