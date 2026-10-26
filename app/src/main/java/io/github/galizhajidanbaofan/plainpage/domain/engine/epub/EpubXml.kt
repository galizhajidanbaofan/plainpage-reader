package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * EPUB 里的 XML 只有三类：container.xml、OPF、NCX（EPUB3 的 nav 是 XHTML，但也按 XML 试一次）。
 *
 * 安全性是这里的第一诉求：EPUB 是用户从外面拿来的文件，天然不可信。
 * 因此默认拒掉外部实体解析（XXE）与外部 DTD 加载——解析器的默认行为恰恰是允许的。
 * 有些老 EPUB 的 NCX 带 DOCTYPE，禁掉「加载」而不是禁掉「出现」是刻意的兼容取舍。
 */
internal object EpubXml {

    fun parse(bytes: ByteArray): Document = try {
        newBuilder().parse(ByteArrayInputStream(bytes))
    } catch (error: SAXException) {
        throw EpubFormatException(EpubFormatException.Kind.CORRUPTED, "EPUB 内的 XML 结构损坏", error)
    } catch (error: IOException) {
        throw EpubFormatException(EpubFormatException.Kind.CORRUPTED, "EPUB 内的 XML 读不出来", error)
    } catch (error: ParserConfigurationException) {
        throw EpubFormatException(EpubFormatException.Kind.CORRUPTED, "XML 解析器不可用", error)
    }

    private fun newBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        // 这些开关在 Android/桌面 JVM 上不一定都支持，逐个 runCatching：能关就关，关不掉不阻断解析。
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        // 用字面量而不是 XMLConstants.ACCESS_EXTERNAL_*：Android 的 XMLConstants 没有这两个常量，
        // 编译期会直接报 Unresolved reference。
        runCatching { factory.setAttribute(ACCESS_EXTERNAL_DTD, "") }
        runCatching { factory.setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }

        val builder = factory.newDocumentBuilder()
        // 兜底：任何外部实体都解析成空串，绝不发起 IO。
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return builder
    }

    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
}

/**
 * 按标签名找元素，忽略命名空间。
 *
 * EPUB2 常用无前缀的 `<item>`，EPUB3 可能给 `dc:` 或 `opf:` 前缀；
 * 阅读器关心的是本地名，逐个命名空间匹配既啰嗦又容易漏。
 */
internal fun Document.elements(tag: String): List<Element> {
    val byLocalName = getElementsByTagNameNS("*", tag).asElements()
    if (byLocalName.isNotEmpty()) return byLocalName
    // 没有命名空间的畸形文档：退化成按标签全名找。
    return getElementsByTagName(tag).asElements()
}

internal fun Element.attribute(name: String): String? {
    if (hasAttribute(name)) return getAttribute(name).takeIf { it.isNotBlank() }
    val attributes = attributes ?: return null
    for (index in 0 until attributes.length) {
        val node = attributes.item(index)
        val local = node.localName ?: node.nodeName.substringAfter(':')
        if (local == name) return node.nodeValue?.takeIf { it.isNotBlank() }
    }
    return null
}

/** 取直接子元素中指定标签的文本，去首尾空白；没有则 null。 */
internal fun Element.childText(tag: String): String? {
    val children = childNodes
    for (index in 0 until children.length) {
        val node = children.item(index)
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            if (element.tagName.substringAfter(':') == tag) {
                return element.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
    }
    return null
}

/** 取第一个直接子元素（按本地名匹配）。 */
internal fun Element.childElement(tag: String): Element? {
    val children = childNodes
    for (index in 0 until children.length) {
        val node = children.item(index)
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            if (element.tagName.substringAfter(':') == tag) return element
        }
    }
    return null
}

private fun NodeList.asElements(): List<Element> {
    val result = ArrayList<Element>(length)
    for (index in 0 until length) {
        (item(index) as? Element)?.let(result::add)
    }
    return result
}
