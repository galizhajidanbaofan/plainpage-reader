package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.charset.StandardCharsets

/** 目录项：标题 + 指向的正文资源（已解析成压缩包内路径）。 */
internal data class EpubNavPoint(
    val title: String,
    val href: String,
    val level: Int,
)

/**
 * 目录解析：NCX（EPUB2）→ nav.xhtml（EPUB3）→ spine 兜底。
 *
 * 三级回退的理由：目录是"可跳转"的核心，但真实文件里三者任一都可能缺。
 * 全都没有时至少按 spine 给出可点击的章节列表，而不是一个空目录。
 */
internal object EpubOutlineParser {

    fun read(archive: EpubArchive, pkg: EpubPackageInfo): List<EpubNavPoint> {
        val fromNcx = pkg.ncxPath?.let { readNcx(archive, it) }.orEmpty()
        if (fromNcx.isNotEmpty()) return fromNcx

        val fromNav = pkg.navPath?.let { readNav(archive, it) }.orEmpty()
        if (fromNav.isNotEmpty()) return fromNav

        // 兜底不用文件名当标题：真实书里这些名字是 "ch1"、"cover"、"titlepage"、"9"，
        // 落到目录和正文里就是占位符一样的乱码，宁可给中性序号。
        return pkg.spine.mapIndexed { index, href ->
            EpubNavPoint(
                title = "第 ${index + 1} 节",
                href = href,
                level = 0,
            )
        }
    }

    private fun readNcx(archive: EpubArchive, ncxPath: String): List<EpubNavPoint> {
        val bytes = archive.readBytesIfPresent(ncxPath, EpubLimits.MAX_XML_BYTES) ?: return emptyList()
        // NCX 坏了不该让整本打不开：目录是增强项，正文才是主体。
        val document = runCatching { EpubXml.parse(bytes) }.getOrElse { return emptyList() }
        val navMap = document.elements("navMap").firstOrNull() ?: return emptyList()
        val baseDir = ZipPaths.directoryOf(ncxPath)
        val result = ArrayList<EpubNavPoint>()
        collectNavPoints(navMap, baseDir, level = 0, out = result)
        return result
    }

    private fun collectNavPoints(
        parent: Element,
        baseDir: String,
        level: Int,
        out: MutableList<EpubNavPoint>,
    ) {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            if (element.tagName.substringAfter(':') != "navPoint") continue

            val title = element.childText("navLabel")
            val src = element.childElement("content")?.attribute("src")
            val href = src?.let { ZipPaths.resolve(baseDir, it) }
            if (title != null && href != null) {
                out.add(EpubNavPoint(title = title, href = href, level = level))
            }
            // 子节点继续往深一层；即使当前节点没有标题也不丢子树。
            collectNavPoints(element, baseDir, level + 1, out)
        }
    }

    private fun readNav(archive: EpubArchive, navPath: String): List<EpubNavPoint> {
        val text = archive.readTextIfPresent(navPath, EpubLimits.MAX_CHAPTER_BYTES) ?: return emptyList()
        val document = runCatching { EpubXml.parse(text.toByteArray(StandardCharsets.UTF_8)) }
            .getOrElse { return emptyList() }
        val nav = document.elements("nav").firstOrNull { element ->
            element.attribute("type")?.split(' ')?.any { it.equals("toc", ignoreCase = true) } == true
        } ?: document.elements("nav").firstOrNull() ?: return emptyList()
        val baseDir = ZipPaths.directoryOf(navPath)

        val result = ArrayList<EpubNavPoint>()
        for (anchor in document.elements("a")) {
            if (!isInside(anchor, nav)) continue
            val title = anchor.textContent?.trim().orEmpty()
            val href = anchor.attribute("href")?.let { ZipPaths.resolve(baseDir, it) } ?: continue
            if (title.isEmpty()) continue
            result.add(EpubNavPoint(title = title, href = href, level = depthInsideNav(anchor, nav)))
        }
        return result
    }

    private fun isInside(node: Node, ancestor: Element): Boolean {
        var current: Node? = node.parentNode
        while (current != null) {
            if (current === ancestor) return true
            current = current.parentNode
        }
        return false
    }

    /** 用 <ol> 的嵌套层数近似章节层级；nav 本身那一层算 0。 */
    private fun depthInsideNav(node: Node, nav: Element): Int {
        var depth = 0
        var current: Node? = node.parentNode
        while (current != null && current !== nav) {
            if (current is Element && current.tagName.substringAfter(':') == "ol") depth++
            current = current.parentNode
        }
        return (depth - 1).coerceAtLeast(0)
    }
}
