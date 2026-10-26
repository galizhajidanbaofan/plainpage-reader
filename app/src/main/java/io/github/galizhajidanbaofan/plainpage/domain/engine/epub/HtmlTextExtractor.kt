package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

/**
 * XHTML → 纯文本。
 *
 * 为什么自研而不用 jsoup：这里的需求只有一个「把标签去掉、把段落分开」，
 * 不需要 CSS 选择器、DOM 树和 HTML5 容错解析器带来的体量；而正文提取是纯字符扫描，
 * 单测里跑得飞快，也不会在畸形文档上抛异常。
 *
 * 不做的事（P0 明确不做）：保留加粗/斜体/脚注链接等行内样式、图片、表格排版。
 * 表格按行输出，够读；样式交给阅读页统一排版。
 */
internal object HtmlTextExtractor {

    private val SKIP_TAGS = setOf("script", "style", "head", "title", "meta", "link", "svg", "iframe", "object")

    private val BLOCK_TAGS = setOf(
        "p", "div", "br", "li", "ul", "ol", "dl", "dt", "dd", "tr", "table", "thead", "tbody",
        "section", "article", "aside", "header", "footer", "main", "nav", "blockquote", "pre",
        "h1", "h2", "h3", "h4", "h5", "h6", "hr", "figure", "figcaption",
    )

    fun extract(markup: String): String {
        val out = StringBuilder(markup.length / 2)
        var index = 0
        var skipDepth = 0

        fun breakLine() {
            // 去掉行尾空格，再补一个换行；已换行则不重复。
            while (out.isNotEmpty() && (out.last() == ' ' || out.last() == '\t')) {
                out.setLength(out.length - 1)
            }
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
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
        return normalize(out)
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
