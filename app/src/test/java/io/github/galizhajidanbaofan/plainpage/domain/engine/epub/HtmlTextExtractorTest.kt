package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HtmlTextExtractorTest {

    @Test
    fun `去掉标签并保留段落边界`() {
        val markup = "<html><body><p>第一段</p><p>第二段</p></body></html>"
        assertEquals("第一段\n第二段", HtmlTextExtractor.extract(markup))
    }

    @Test
    fun `跳过 head script style`() {
        val markup = """
            <html>
              <head><title>标题</title><style>p { color: red; }</style></head>
              <body><p>前<script>var a = 1;</script>后</p></body>
            </html>
        """.trimIndent()
        assertEquals("前后", HtmlTextExtractor.extract(markup))
    }

    @Test
    fun `br 换行且行内标签不产生空行`() {
        val markup = "第一行<br/>第二行<b>加粗</b>结尾"
        assertEquals("第一行\n第二行加粗结尾", HtmlTextExtractor.extract(markup))
    }

    @Test
    fun `实体解码`() {
        val markup = "<p>&amp; &lt;x&gt; &quot;引号&quot; &#8212; &#x2014; &hellip;</p>"
        assertEquals("& <x> \"引号\" — — …", HtmlTextExtractor.extract(markup))
    }

    @Test
    fun `未知实体原样保留`() {
        assertEquals("a &unknown; b", HtmlTextExtractor.extract("<p>a &unknown; b</p>"))
    }

    @Test
    fun `连续空白压成一个空格`() {
        assertEquals("甲 乙 丙", HtmlTextExtractor.extract("<p>甲 \n\t   乙     丙</p>"))
    }

    @Test
    fun `注释不参与输出`() {
        assertEquals("可见", HtmlTextExtractor.extract("<p>可<!-- 隐藏 -->见</p>"))
    }

    @Test
    fun `空段落被丢弃不产生连续空行`() {
        assertEquals("一\n二", HtmlTextExtractor.extract("<p>一</p><p>   </p><p>二</p>"))
    }

    @Test
    fun `svg 内容整段跳过`() {
        val markup = "<p>前</p><svg><text>图形文字</text></svg><p>后</p>"
        val text = HtmlTextExtractor.extract(markup)
        assertEquals("前\n后", text)
        assertFalse(text.contains("图形文字"))
    }

    @Test
    fun `没有标签的纯文本原样返回`() {
        assertEquals("就是一段文字", HtmlTextExtractor.extract("就是一段文字"))
    }

    @Test
    fun `未闭合标签不抛异常`() {
        assertEquals("半段", HtmlTextExtractor.extract("<p>半段<b>"))
    }
}
