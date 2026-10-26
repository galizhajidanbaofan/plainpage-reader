package io.github.galizhajidanbaofan.plainpage.domain.engine

/**
 * 正文里的一处插图：出现在全书文本的 [at] 这个字符间隙上。
 *
 * 为什么用「字符偏移」而不是页码：页码会随字号、屏幕、翻页方式变，
 * 字符偏移不变。阅读页据此把插图安排到它所属的那一屏之后，进度也仍然记字符偏移。
 */
data class PictureRef(
    val at: Int,
    /** 压缩包内路径（已归一化），引擎据此取字节。 */
    val path: String,
    val alt: String? = null,
)

/**
 * 「正文里带图」的引擎能力。TXT 不实现它，EPUB 实现。
 *
 * 单独拆接口而不是塞进 [PaginatedEngine]：PDF 是按页位图，TXT 没有图，
 * 只有 EPUB 需要这套东西，硬塞会给另外两个实现增加无意义的空方法。
 */
interface PictureSource {

    /** 按 [PictureRef.at] 升序排列，已剔除小图标与解析不出的引用。 */
    val pictures: List<PictureRef>

    /** 取图片原始字节；不存在或超限返回 null（UI 把这一页当空页跳过，不影响读正文）。 */
    suspend fun pictureBytes(path: String): ByteArray?
}
