package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import io.github.galizhajidanbaofan.plainpage.domain.engine.PictureRef

/**
 * 一章在全书正文里的字符区间。
 *
 * [charStart] 指向该章标题在正文里的起点（目录跳转与"当前章节"判断都用它），
 * [charEnd] 是排他上界。区间首尾相接，保证任意偏移都能落到唯一一章。
 */
internal data class EpubChapter(
    val href: String,
    val title: String,
    val charStart: Int,
    val charEnd: Int,
    val level: Int,
)

/**
 * 全书索引：正文拼成一整串，加上章节区间表。
 *
 * 为什么拼成一整串而不是"按章按需读"：分页、翻页、进度百分比都需要"全书连续字符坐标"，
 * 拼接让这些逻辑与 TXT 完全共用；代价是打开时把正文读进内存（上限 [EpubLimits.MAX_TOTAL_CHARS] 已封顶）。
 * 真实的按需加载要等分页与进度改用"章节+章内偏移"两段式坐标，那属于 P1 的性能优化，不在此刻假装做到。
 */
internal class EpubIndex(
    val text: String,
    val chapters: List<EpubChapter>,
    /** 正文超过上限，后面的章节没有读入。 */
    val truncated: Boolean,
    /** 插图锚点，按 [PictureRef.at] 升序。 */
    val pictures: List<PictureRef> = emptyList(),
) {
    val charCount: Int get() = text.length

    /** 偏移落在哪一章。 */
    fun chapterAt(offset: Int): EpubChapter? {
        if (chapters.isEmpty()) return null
        val clamped = offset.coerceIn(0, maxOf(0, charCount - 1))
        return chapters.lastOrNull { it.charStart <= clamped } ?: chapters.first()
    }

    fun chapterByHref(href: String): EpubChapter? = chapters.firstOrNull { it.href == href }
}

/** 把 spine 里的 XHTML 依次抽成文本，并记录每章的字符区间。 */
internal object EpubIndexer {

    fun build(archive: EpubArchive, pkg: EpubPackageInfo): EpubIndex {
        val outline = EpubOutlineParser.read(archive, pkg)
        val titles = LinkedHashMap<String, String>()
        val levels = HashMap<String, Int>()
        for (point in outline) {
            titles.putIfAbsent(point.href, point.title)
            levels.putIfAbsent(point.href, point.level)
        }

        val builder = StringBuilder()
        val chapters = ArrayList<EpubChapter>()
        val pictures = ArrayList<PictureRef>()
        var truncated = false

        for (href in pkg.spine) {
            if (builder.length >= EpubLimits.MAX_TOTAL_CHARS) {
                truncated = true
                break
            }
            val markup = archive.readTextIfPresent(href, EpubLimits.MAX_CHAPTER_BYTES) ?: continue
            val extracted = HtmlTextExtractor.extract(markup)
            val body = extracted.text
            if (body.isEmpty()) continue

            val title = titles[href]
                ?: ZipPaths.stemOf(href).ifBlank { "第 ${chapters.size + 1} 节" }
            if (builder.isNotEmpty()) builder.append('\n')
            val start = builder.length
            builder.append(title.trim()).append('\n')
            builder.append(body)
            // 图片偏移 = 本章起点 + 标题行长度 + 它在整章文本里的位置。
            // 解析不出来的引用（绝对 URL、data:、越界的 ../）直接丢：坏引用不该毁掉整本书。
            val bodyStart = start + title.trim().length + 1
            val chapterDir = href.substringBeforeLast('/', "")
            extracted.images.forEach { image ->
                val path = ZipPaths.resolve(chapterDir, image.src) ?: return@forEach
                pictures.add(
                    PictureRef(
                        at = bodyStart + image.offset.coerceIn(0, body.length),
                        path = path,
                        alt = image.alt,
                    ),
                )
            }
            chapters.add(
                EpubChapter(
                    href = href,
                    title = title,
                    charStart = start,
                    charEnd = builder.length,
                    level = levels[href] ?: 0,
                ),
            )
        }

        if (chapters.isEmpty()) {
            throw EpubFormatException(
                EpubFormatException.Kind.EMPTY_CONTENT,
                "这本 EPUB 的正文解析不出文字",
            )
        }

        // 封面图通常只写在 OPF 里，任何正文 XHTML 都不引用它；不单独补一张，封面就永远不显示。
        // 正文里已经引用过同一张图时不重复插（有的书第一页就是封面，会引两遍）。
        val cover = pkg.coverImage
        if (cover != null &&
            pictures.size < EpubLimits.MAX_PICTURES &&
            pictures.none { it.path.equals(cover, ignoreCase = true) }
        ) {
            pictures.add(0, PictureRef(at = 0, path = cover, alt = pkg.title))
        }

        return EpubIndex(
            text = builder.toString(),
            chapters = chapters,
            truncated = truncated,
            pictures = pictures.take(EpubLimits.MAX_PICTURES),
        )
    }
}
