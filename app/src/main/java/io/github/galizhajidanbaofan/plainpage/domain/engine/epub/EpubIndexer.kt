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

    /** 章节之间的分隔换行；抽成常量是因为换行字符字面量手写容易出错。 */
    private const val NEWLINE: Char = 10.toChar()

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
        // 同一张图在正文里被引用多次是常态：星标、分隔线、题花这类装饰图在有些书里
        // 会被每一章重复引用上百次（实测某本合集里同一个 3 KB 的 star.png 出现 1125 次）。
        // 若每次引用都插一页"全屏插图"，插图列表很快被装饰图占满、真插图反而被截断，
        // 所以这里按路径去重：一张图只占一页，位置取它第一次出现的地方。
        val seenPictures = HashSet<String>()
        var truncated = false

        for (href in pkg.spine) {
            if (builder.length >= EpubLimits.MAX_TOTAL_CHARS) {
                truncated = true
                break
            }
            val markup = archive.readTextIfPresent(href, EpubLimits.MAX_CHAPTER_BYTES) ?: continue
            val extracted = HtmlTextExtractor.extract(markup)
            val body = extracted.text
            // 只有图、没有字的页（封面页、整页插画页）正文是空的，但这种页恰恰是插图的所在地：
            // 若在这里按"没文字"整页跳过，图和它的锚点会一起消失（实测某本合集丢了 41 张插图）。
            if (body.isEmpty() && extracted.images.isEmpty()) continue

            val chapterDir = href.substringBeforeLast('/', "")
            val pagePictures = ArrayList<PictureRef>()
            extracted.images.forEach { image ->
                val path = ZipPaths.resolve(chapterDir, image.src) ?: return@forEach
                if (!seenPictures.add(path.lowercase())) return@forEach
                // 体积过小的多半是项目符号 / 分隔线 / 角花，单独占一页只会变成空白页；
                // 体积过大的按畸形文件处理，渲染阶段也读不出来。
                val size = archive.sizeOf(path) ?: return@forEach
                if (size < EpubLimits.MIN_PICTURE_BYTES || size > EpubLimits.MAX_PICTURE_BYTES) {
                    return@forEach
                }
                // 再看像素尺寸：体积正常的 40×40 装饰方块也不该占一页。
                val header = archive.readPrefixIfPresent(path, EpubLimits.MAX_PICTURE_HEADER_BYTES)
                val dimensions = header?.let(ImageHeaders::sizeOf)
                if (dimensions != null &&
                    (dimensions.first < EpubLimits.MIN_PICTURE_EDGE ||
                        dimensions.second < EpubLimits.MIN_PICTURE_EDGE)
                ) {
                    return@forEach
                }
                pagePictures.add(PictureRef(at = image.offset, path = path, alt = image.alt))
            }

            if (body.isEmpty()) {
                // 纯图页：不写标题、不占正文、也不进目录。
                // 早先给它补一个「文件名当标题」的正文行，结果正文里冒出 cover / titlepage 这种字样，
                // 而图被锚在那一行之后 → 看起来就是"文字有占位符、图跑到下一页"。
                pagePictures.forEach { pictures.add(it.copy(at = builder.length)) }
                continue
            }

            // 目录里没有这一章（合并型 EPUB 的卷内页很常见）时，不要拿文件名当标题：
            // "9"、"cover"、"titlepage" 落到正文里就是乱码一样的存在。
            val title = titles[href] ?: "第 ${chapters.size + 1} 节"
            val titleLine = title.trim()
            // XHTML 里常常自带头一行大标题，而目录标题就是它：直接拼会看到标题出现两次。
            // 只在完全相同时丢弃，避免把"第一章 雪夜"和"雪夜"这种近似的正文首句误删。
            var bodyText = body
            var skipped = 0
            val firstLineEnd = bodyText.indexOf(NEWLINE)
            val firstLine = if (firstLineEnd >= 0) bodyText.substring(0, firstLineEnd) else bodyText
            if (firstLine.trim() == titleLine) {
                skipped = if (firstLineEnd >= 0) firstLineEnd + 1 else bodyText.length
                bodyText = if (firstLineEnd >= 0) bodyText.substring(firstLineEnd + 1) else ""
            }

            if (builder.isNotEmpty()) builder.append(NEWLINE)
            val start = builder.length
            builder.append(titleLine).append(NEWLINE)
            builder.append(bodyText)
            // 图片偏移 = 本章起点 + 标题行长度 + 它在整章文本里的位置（扣掉被丢弃的重复标题行）。
            // 解析不出来的引用（绝对 URL、data:、越界的 ../）直接丢：坏引用不该毁掉整本书。
            val bodyStart = start + titleLine.length + 1
            pagePictures.forEach { ref ->
                val inBody = (ref.at - skipped).coerceIn(0, bodyText.length)
                pictures.add(ref.copy(at = bodyStart + inBody))
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
