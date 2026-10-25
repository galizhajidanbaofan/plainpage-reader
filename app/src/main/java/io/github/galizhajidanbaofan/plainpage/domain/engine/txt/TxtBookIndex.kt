package io.github.galizhajidanbaofan.plainpage.domain.engine.txt

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.coroutineContext

/**
 * 字符偏移 → 字节偏移的锚点。
 *
 * 有它才能"从中间开始读"：随机访问文件必须落在字节边界上，
 * 而进度记的是字符偏移，两者之间只能靠扫描时留下的锚点换算。
 */
data class TxtCheckpoint(val charOffset: Int, val byteOffset: Long)

/**
 * 一份 TXT 的索引结果。
 *
 * 注意这里**没有全文**：100MB 的书如果常驻 String，光是 UTF-16 就要 200MB，
 * 中端机直接 OOM。正文按需从文件里读（[TxtPageReader]）。
 */
data class TxtIndex(
    val encoding: TextEncoding,
    /** 全文 UTF-16 字符数。 */
    val charCount: Int,
    val chapters: List<TxtChapter>,
    val checkpoints: List<TxtCheckpoint>,
    /** 解码时出现过替换字符（说明原文件确实是坏的，或编码猜错了）。 */
    val lossyDecoding: Boolean,
    /** 章节数超过上限，后面的没再切。 */
    val chaptersTruncated: Boolean,
)

/** 索引阶段的失败原因，由引擎翻译成 [io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException]。 */
sealed class TxtIndexException(message: String) : Exception(message) {
    object Missing : TxtIndexException("文件不存在")
    object Empty : TxtIndexException("文件是空的")
    class TooLarge(val bytes: Long) : TxtIndexException("文件太大：$bytes 字节")
}

/**
 * 流式建立 TXT 索引：边解码边喂章节扫描器，同时每 [checkpointChars] 个字符记一个字节锚点。
 *
 * 内存占用与文件大小无关（只有一个读缓冲 + 当前行），所以 100MB 的 TXT 也不会 ANR/OOM。
 */
class TxtBookIndexer(
    private val chunkBytes: Int = DEFAULT_CHUNK_BYTES,
    private val checkpointChars: Int = DEFAULT_CHECKPOINT_CHARS,
    private val maxBytes: Long = MAX_TXT_BYTES,
    private val maxChapters: Int = TxtChapterScanner.MAX_CHAPTERS,
) {

    suspend fun index(file: File): TxtIndex {
        if (!file.isFile) throw TxtIndexException.Missing
        val length = file.length()
        if (length <= 0L) throw TxtIndexException.Empty
        if (length > maxBytes) throw TxtIndexException.TooLarge(length)

        val detection = TextEncodingDetector.detect(readSample(file, TextEncodingDetector.SAMPLE_BYTES))
        val charset = detection.encoding.charset
        val decoder = newDecoder(charset)

        val scanner = TxtChapterScanner(maxChapters)
        // 锚点从 BOM 之后开始：否则"从第 0 个字符读"会把 BOM 当成正文的第一个字符。
        val checkpoints = ArrayList<TxtCheckpoint>()
        checkpoints += TxtCheckpoint(0, detection.bomLength.toLong())
        var chars = 0
        var sawReplacement = false

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(detection.bomLength.toLong())
            val byteBuffer = ByteArray(chunkBytes)
            val charBuffer = CharBuffer.allocate(chunkBytes)
            var leftover = ByteArray(0)
            var bytesConsumed = detection.bomLength.toLong()

            while (true) {
                coroutineContext.ensureActive()
                val read = raf.read(byteBuffer)
                val endOfInput = read < 0
                val source = if (endOfInput) leftover else concat(leftover, byteBuffer, read)
                val inBuffer = ByteBuffer.wrap(source)

                while (true) {
                    charBuffer.clear()
                    val result = decoder.decode(inBuffer, charBuffer, endOfInput)
                    charBuffer.flip()
                    if (charBuffer.hasRemaining()) {
                        chars += charBuffer.remaining()
                        if (!sawReplacement) {
                            sawReplacement = charBuffer.toString().indexOf(REPLACEMENT_CHAR) >= 0
                        }
                        charBuffer.rewind()
                        scanner.append(charBuffer)
                    }
                    if (result.isOverflow) continue
                    break
                }

                if (endOfInput) {
                    drain(decoder, charBuffer, scanner) { replaced -> if (replaced) sawReplacement = true }
                    break
                }

                leftover = if (inBuffer.hasRemaining()) {
                    ByteArray(inBuffer.remaining()).also { inBuffer.get(it) }
                } else {
                    ByteArray(0)
                }
                bytesConsumed = raf.filePointer - leftover.size
                if (chars - checkpoints.last().charOffset >= checkpointChars) {
                    checkpoints += TxtCheckpoint(chars, bytesConsumed)
                }
            }
        }

        val scanned = scanner.finish(chars)
        return TxtIndex(
            encoding = detection.encoding,
            charCount = chars,
            chapters = scanned.chapters,
            checkpoints = checkpoints,
            lossyDecoding = detection.lossy || sawReplacement,
            chaptersTruncated = scanned.truncated,
        )
    }

    /** 解码收尾：flush 出解码器内部残留的字符（UTF-16 之类可能有）。 */
    private inline fun drain(
        decoder: CharsetDecoder,
        charBuffer: CharBuffer,
        scanner: TxtChapterScanner,
        onReplacement: (Boolean) -> Unit,
    ) {
        repeat(2) {
            charBuffer.clear()
            val result = decoder.flush(charBuffer)
            charBuffer.flip()
            if (charBuffer.hasRemaining()) {
                val text = charBuffer.toString()
                if (text.indexOf(REPLACEMENT_CHAR) >= 0) onReplacement(true)
                scanner.append(text)
            }
            charBuffer.clear()
            if (!result.isOverflow) return
        }
    }

    private fun readSample(file: File, limit: Int): ByteArray =
        RandomAccessFile(file, "r").use { raf ->
            val size = minOf(file.length(), limit.toLong()).toInt()
            val bytes = ByteArray(size)
            raf.readFully(bytes)
            bytes
        }

    companion object {
        /** 单文件上限。100MB 的 TXT 要能开，给到 512MB 仍有边界。 */
        const val MAX_TXT_BYTES: Long = 512L * 1024 * 1024

        const val DEFAULT_CHUNK_BYTES: Int = 256 * 1024
        const val DEFAULT_CHECKPOINT_CHARS: Int = 32 * 1024

        private const val REPLACEMENT_CHAR = '\uFFFD'
    }
}

/**
 * 按需从文件里读正文。
 *
 * 每次读都从最近的锚点开始解码、丢掉多余的字符，代价是"锚点间隔内的一次解码"，
 * 换来的是**内存里永远只有一小段文本**。
 */
class TxtPageReader(
    file: File,
    private val index: TxtIndex,
    private val chunkBytes: Int = 64 * 1024,
) : AutoCloseable {

    private val raf = RandomAccessFile(file, "r")

    /** 读 [startChar] 起、最多 [count] 个字符；越界会被夹紧，不会抛异常。 */
    suspend fun read(startChar: Int, count: Int): String {
        if (count <= 0 || index.charCount <= 0) return ""
        val from = startChar.coerceIn(0, index.charCount)
        if (from >= index.charCount) return ""
        val target = minOf(index.charCount - from, count)

        val checkpoint = index.checkpoints.lastOrNull { it.charOffset <= from }
            ?: index.checkpoints.first()
        var skip = from - checkpoint.charOffset

        raf.seek(checkpoint.byteOffset)
        val decoder = newDecoder(index.encoding.charset)
        val out = StringBuilder(target + 16)
        val byteBuffer = ByteArray(chunkBytes)
        val charBuffer = CharBuffer.allocate(chunkBytes)
        var leftover = ByteArray(0)

        while (out.length < target) {
            coroutineContext.ensureActive()
            val read = raf.read(byteBuffer)
            val endOfInput = read < 0
            val source = if (endOfInput) leftover else concat(leftover, byteBuffer, read)
            if (source.isEmpty()) break
            val inBuffer = ByteBuffer.wrap(source)

            while (out.length < target) {
                charBuffer.clear()
                val result = decoder.decode(inBuffer, charBuffer, endOfInput)
                charBuffer.flip()
                if (charBuffer.hasRemaining()) {
                    val piece = charBuffer.toString()
                    charBuffer.clear()
                    var offset = 0
                    if (skip > 0) {
                        val drop = minOf(skip, piece.length)
                        skip -= drop
                        offset = drop
                    }
                    if (offset < piece.length) {
                        out.append(piece, offset, piece.length)
                    }
                }
                if (result.isOverflow) continue
                break
            }

            if (endOfInput) break
            leftover = if (inBuffer.hasRemaining()) {
                ByteArray(inBuffer.remaining()).also { inBuffer.get(it) }
            } else {
                ByteArray(0)
            }
        }

        return if (out.length > target) out.substring(0, target) else out.toString()
    }

    override fun close() {
        raf.close()
    }
}

private fun newDecoder(charset: Charset): CharsetDecoder =
    charset.newDecoder()
        // 永远替换而不报错：能打开总比"编码错误"的空白页强，[TxtIndex.lossyDecoding] 负责提示。
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

private fun concat(prefix: ByteArray, buffer: ByteArray, length: Int): ByteArray {
    if (prefix.isEmpty()) return buffer.copyOf(length)
    val merged = ByteArray(prefix.size + length)
    prefix.copyInto(merged, 0)
    buffer.copyInto(merged, prefix.size, 0, length)
    return merged
}
