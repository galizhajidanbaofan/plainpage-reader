package io.github.galizhajidanbaofan.plainpage.domain.engine.txt

/**
 * 一章（TXT 的"章"是正则切出来的，不是文件结构）。
 *
 * [charStart] / [charEnd] 都是解码后的 UTF-16 字符偏移，左闭右开，
 * 与 [io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation.Txt.charOffset] 同一坐标系。
 */
data class TxtChapter(
    val title: String,
    val charStart: Int,
    val charEnd: Int,
)

/** 章节标题的判定规则。抽成 object 是为了让规则本身能被单测直接打。 */
object TxtHeadings {

    private const val CN_DIGITS = "0-9０-９一二三四五六七八九十百千零〇两"

    /** 第12章 / 第三回 / 卷二 / 第 5 节 / 第五篇 …… 后面可以带标题文字。 */
    private val numbered = Regex("^第[$CN_DIGITS]{1,12}[章节回卷篇集部话](.*)$")

    /** 章节名不带"第"的常见写法。故意**不含**"正文"：很多书里"正文"是正文里的分隔词，会误切。 */
    private val special = Regex("^(序章|序言|自序|代序|楔子|引子|前言|后记|跋|尾声|终章|番外)(.*)$")

    /** 英文小说。 */
    private val english = Regex("^chapter\\s+[0-9ivxlcdm]{1,8}(.*)$", RegexOption.IGNORE_CASE)

    /** 标题最长多少字符还算标题（再长就是正文里恰好提到"第一章"的句子）。 */
    private const val MAX_TITLE_LENGTH = 40

    /** 这些标点出现说明是句子而不是标题。 */
    private const val SENTENCE_MARKS = "。！？；，、" 

    /**
     * @param line 已去掉行首尾空白的整行
     */
    fun isHeading(line: String): Boolean {
        if (line.isEmpty() || line.length > MAX_TITLE_LENGTH) return false
        if (line.any { it in SENTENCE_MARKS }) return false
        if (line.endsWith("：") || line.endsWith(":")) return false
        return numbered.matches(line) || special.matches(line) || english.matches(line)
    }
}

/**
 * 增量式章节扫描器：喂字符串块进去，最后要章节列表。
 *
 * 为什么要增量：100MB 的 TXT 不能先整体 toString 再逐行正则（内存翻倍 + 必然 ANR），
 * 所以按流式解码出来的块喂进来，内部只留当前这一行（且有长度上限）。
 *
 * 用法：`append(chunk)` 多次 → `finish(charCount)`。
 */
class TxtChapterScanner(private val maxChapters: Int = MAX_CHAPTERS) {

    data class Result(val chapters: List<TxtChapter>, val truncated: Boolean)

    private class Start(val title: String, val charStart: Int)

    private val pending = StringBuilder()
    private val starts = ArrayList<Start>()
    private var lineStart = 0
    private var cursor = 0
    private var truncated = false
    private var finished = false

    fun append(text: CharSequence) {
        check(!finished) { "append() 不能再 finish() 之后调用" }
        var i = 0
        val length = text.length
        while (i < length) {
            val ch = text[i]
            if (pending.isEmpty()) lineStart = cursor
            when (ch) {
                '\n' -> finishLine()
                '\r' -> Unit // CRLF 里的 CR 直接丢，但偏移仍要算
                else -> if (pending.length < MAX_LINE_LENGTH) pending.append(ch)
            }
            cursor++
            i++
        }
    }

    fun finish(charCount: Int): Result {
        check(!finished) { "finish() 只能调一次" }
        finished = true
        if (pending.isNotEmpty()) finishLine()
        val total = maxOf(charCount, cursor)
        val chapters = ArrayList<TxtChapter>(starts.size + 1)

        var previousStart = -1
        for (start in starts) {
            if (start.charStart <= previousStart) continue // 同一行重复命中，去重
            if (start.charStart >= total) break
            chapters += TxtChapter(start.title, start.charStart, start.charStart)
            previousStart = start.charStart
        }

        if (chapters.isEmpty()) {
            return Result(listOf(TxtChapter(DEFAULT_CHAPTER_TITLE, 0, total)), truncated = false)
        }

        // 第一个章节之前还有正文（版权页/简介），补一个"开篇"，否则读者点目录会丢掉开头。
        val merged = if (chapters.first().charStart > 0) {
            listOf(TxtChapter(OPENING_CHAPTER_TITLE, 0, 0)) + chapters
        } else {
            chapters
        }

        val bounded = merged.mapIndexed { index, chapter ->
            val end = when {
                index + 1 < merged.size -> merged[index + 1].charStart
                else -> total
            }
            chapter.copy(charEnd = maxOf(end, chapter.charStart))
        }
        return Result(bounded, truncated)
    }

    private fun finishLine() {
        if (pending.isEmpty()) return
        val line = pending.toString()
        if (TxtHeadings.isHeading(line)) {
            if (starts.size < maxChapters) {
                starts += Start(line, lineStart)
            } else {
                truncated = true
            }
        }
        pending.clear()
    }

    companion object {
        const val MAX_CHAPTERS = 5_000

        /** 只缓存这么长的行用于标题判定，再长的一定是正文。 */
        private const val MAX_LINE_LENGTH = 120

        const val DEFAULT_CHAPTER_TITLE = "正文"
        const val OPENING_CHAPTER_TITLE = "开篇"
    }
}

/** 供测试与小文本使用的一步到位封装。 */
fun splitTxtChapters(text: String, maxChapters: Int = TxtChapterScanner.MAX_CHAPTERS): List<TxtChapter> {
    val scanner = TxtChapterScanner(maxChapters)
    scanner.append(text)
    return scanner.finish(text.length).chapters
}
