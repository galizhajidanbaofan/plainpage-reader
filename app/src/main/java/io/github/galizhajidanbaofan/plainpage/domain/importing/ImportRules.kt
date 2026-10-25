package io.github.galizhajidanbaofan.plainpage.domain.importing

import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat

/**
 * 导入相关的纯规则，不碰 Android API，便于单元测试。
 *
 * 导入流程里唯一需要「猜」的两件事是**格式**和**显示名**，把它们集中在纯函数里，
 * 出问题时有测试可回归，而不是散在 Activity 的回调里。
 */
object ImportRules {

    /**
     * 单文件体积上限。产品要求覆盖 100MB 级 TXT / 50MB 级 PDF，留 3 倍余量；
     * 超过就明确拒绝并提示，而不是让用户等一个注定 OOM 的导入。
     */
    const val MAX_BYTES: Long = 300L * 1024 * 1024

    private const val MAX_TITLE_LENGTH = 120

    private val ILLEGAL_CHARS = charArrayOf(
        '/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000', '\n', '\r', '\t',
    )

    /**
     * 把来源提供的文件名收敛成可安全落盘、可安全显示的名字。
     * 不保留路径分隔符（防止任何形式的路径穿越），不保留控制字符。
     */
    fun sanitizeFileName(raw: String?): String {
        val cleaned = buildString {
            raw.orEmpty().forEach { ch ->
                append(if (ch in ILLEGAL_CHARS || ch.isISOControl()) '_' else ch)
            }
        }
            .trim()
            .trim('.', ' ')
            .take(MAX_TITLE_LENGTH)
        return cleaned.ifBlank { FALLBACK_TITLE }
    }

    /** 从文件名/displayName 得到书名：去掉最后一个扩展名，再做一次清洗。 */
    fun titleFromFileName(fileName: String?): String {
        val base = fileName.orEmpty().substringBeforeLast('.', fileName.orEmpty())
        return sanitizeFileName(base)
    }

    /**
     * 判定格式：先看扩展名（最可靠），扩展名不认识再退回 MIME。
     * 两者都不认识返回 null —— 调用方据此提示「不支持的文件类型」，绝不强行当 TXT 打开。
     */
    fun detectFormat(displayName: String?, mimeType: String?): BookFormat? {
        displayName?.let { name ->
            BookFormat.fromFileName(name)?.let { return it }
        }
        val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (normalizedMime != null) {
            BookFormat.entries.firstOrNull { it.mimeType == normalizedMime }?.let { return it }
        }
        return null
    }

    /** 私有目录内的落盘文件名：用随机 id 而非原文件名，避免重名与奇怪字符。 */
    fun storageFileName(id: String, format: BookFormat): String = "$id.${format.extension}"

    const val FALLBACK_TITLE: String = "未命名"
}
