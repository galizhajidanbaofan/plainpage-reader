package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class HtmlTextExtractorTest {

    @Test
    fun `去掉标签并保留段落边界`() {
        val markup = "<html><body><p>第一段</p><p>第二段</p></body></html>"
        assertEquals("第一段\n第二段", HtmlTextExtractor.extract(markup).text)
    }

    @Test
    fun `跳过 head script style`() {
        val markup = """
            <html>
              <head><title>标题</title><style>p { color: red; }</style></head>
              <body><p>前<script>var a = 1;</script>后</p></body>
            </html>
        """.trimIndent()
        assertEquals("前后", HtmlTextExtractor.extract(markup).text)
    }

    @Test
    fun `br 换行且行内标签不产生空行`() {
        val markup = "第一行<br/>第二行<b>加粗</b>结尾"
        assertEquals("第一行\n第二行加粗结尾", HtmlTextExtractor.extract(markup).text)
    }

    @Test
    fun `实体解码`() {
        val markup = "<p>&amp; &lt;x&gt; &quot;引号&quot; &#8212; &#x2014; &hellip;</p>"
        assertEquals("& <x> \"引号\" — — …", HtmlTextExtractor.extract(markup).text)
    }

    @Test
    fun `未知实体原样保留`() {
        assertEquals("a &unknown; b", HtmlTextExtractor.extract("<p>a &unknown; b</p>").text)
    }

    @Test
    fun `连续空白压成一个空格`() {
        assertEquals("甲 乙 丙", HtmlTextExtractor.extract("<p>甲 \n\t   乙     丙</p>").text)
    }

    @Test
    fun `注释不参与输出`() {
        assertEquals("可见", HtmlTextExtractor.extract("<p>可<!-- 隐藏 -->见</p>").text)
    }

    @Test
    fun `空段落被丢弃不产生连续空行`() {
        assertEquals("一\n二", HtmlTextExtractor.extract("<p>一</p><p>   </p><p>二</p>").text)
    }

    @Test
    fun `svg 里的矢量文字不进正文，但整棵 svg 不再被跳过`() {
        // 以前这段断言的是「整棵 svg 被跳过」——那恰好就是真机上插图不显示的原因，
        // 所以断言改成：矢量文字照旧不要，但图必须能被识别。
        val markup = "<p>前</p><svg><text>图形文字</text><image href=\"a.png\"/></svg><p>后</p>"
        val result = HtmlTextExtractor.extract(markup)
        assertEquals("前\n后", result.text)
        assertFalse(result.text.contains("图形文字"))
        assertEquals("svg 里的 image 必须被当成插图", 1, result.images.size)
    }

    @Test
    fun `没有标签的纯文本原样返回`() {
        assertEquals("就是一段文字", HtmlTextExtractor.extract("就是一段文字").text)
    }

    @Test
    fun `未闭合标签不抛异常`() {
        assertEquals("半段", HtmlTextExtractor.extract("<p>半段<b>").text)
    }

    @Test
    fun `图片不进正文，但记下位置与说明`() {
        val result = HtmlTextExtractor.extract(
            """<p>甲段</p><img src="Images/snow.png" alt="窗外的雪"/><p>乙段</p>""",
        )
        assertEquals("甲段\n乙段", result.text)
        assertEquals(1, result.images.size)
        val image = result.images.single()
        assertEquals("Images/snow.png", image.src)
        assertEquals("窗外的雪", image.alt)
        // "甲段\n" 之后就是图该出现的位置
        assertEquals(3, image.offset)
    }

    @Test
    fun `数据内联图与空的 src 被跳过`() {
        val result = HtmlTextExtractor.extract(
            """<p>甲</p><img src="data:image/png;base64,AAAA"/><img src=""/><p>乙</p>""",
        )
        assertTrue(result.images.isEmpty())
    }

    @Test
    fun `svg 内部的 image 算插图，xlink href 也认`() {
        val result = HtmlTextExtractor.extract(
            "<p>甲</p><svg xmlns:xlink=\"http://www.w3.org/1999/xlink\">" +
                "<image xlink:href=\"../Images/snow.jpg\"/></svg><p>乙</p>",
        )
        assertEquals(1, result.images.size)
        assertEquals("../Images/snow.jpg", result.images.single().src)
    }

    @Test
    fun `图片偏移总在正文长度之内`() {
        val result = HtmlTextExtractor.extract(
            """<img src="a.png"/><p>正文</p><img src="b.png"/>""",
        )
        assertEquals(2, result.images.size)
        result.images.forEach { image ->
            assertTrue("偏移 ${image.offset} 应落在 0..${result.text.length}", image.offset in 0..result.text.length)
        }
    }
}
