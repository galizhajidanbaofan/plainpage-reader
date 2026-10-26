package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

/** OPF manifest 里的一项。href 已经解析成相对压缩包根的路径。 */
internal data class ManifestItem(
    val id: String,
    val path: String,
    val mediaType: String,
    val properties: String,
)

/**
 * 解析出来的 OPF 信息。字段都是"已经解好路径"的，调用方不需要再拼目录。
 */
internal data class EpubPackageInfo(
    val packagePath: String,
    val baseDir: String,
    val title: String?,
    val author: String?,
    /** 阅读顺序（已去掉 linear="no" 的辅助资源、已去重、已解析路径）。 */
    val spine: List<String>,
    val ncxPath: String?,
    val navPath: String?,
)

/** container.xml：EPUB 里唯一"位置固定"的文件，指向真正的 OPF。 */
internal object EpubContainer {

    private const val CONTAINER_PATH = "META-INF/container.xml"

    fun packagePath(archive: EpubArchive): String {
        val bytes = archive.readBytesIfPresent(CONTAINER_PATH, EpubLimits.MAX_XML_BYTES)
            ?: throw EpubFormatException(
                EpubFormatException.Kind.CORRUPTED,
                "EPUB 缺少 META-INF/container.xml",
            )
        val document = EpubXml.parse(bytes)
        val fullPath = document.elements("rootfile")
            .mapNotNull { it.attribute("full-path") }
            .firstOrNull { it.isNotBlank() }
            ?: throw EpubFormatException(
                EpubFormatException.Kind.CORRUPTED,
                "container.xml 里没有 rootfile",
            )
        return ZipPaths.normalize(fullPath) ?: throw EpubFormatException(
            EpubFormatException.Kind.CORRUPTED,
            "container.xml 指向的路径不合法",
        )
    }
}

/**
 * OPF（package document）解析。
 *
 * 只取阅读必需的四样：元数据（标题/作者）、spine 顺序、NCX 位置、EPUB3 nav 位置。
 * 样式表、字体、图片一律不读——P0 是纯文本重排，读进来只会拖慢打开速度。
 */
internal object EpubPackageParser {

    fun read(archive: EpubArchive): EpubPackageInfo {
        val packagePath = EpubContainer.packagePath(archive)
        val bytes = archive.readBytesIfPresent(packagePath, EpubLimits.MAX_XML_BYTES)
            ?: throw EpubFormatException(
                EpubFormatException.Kind.CORRUPTED,
                "EPUB 缺少 OPF：$packagePath",
            )
        val document = EpubXml.parse(bytes)
        val baseDir = ZipPaths.directoryOf(packagePath)

        val manifest = document.elements("item").mapNotNull { element ->
            val id = element.attribute("id") ?: return@mapNotNull null
            val path = element.attribute("href")?.let { ZipPaths.resolve(baseDir, it) } ?: return@mapNotNull null
            ManifestItem(
                id = id,
                path = path,
                mediaType = element.attribute("media-type").orEmpty(),
                properties = element.attribute("properties").orEmpty(),
            )
        }
        val byId = manifest.associateBy { it.id }

        val spineElement = document.elements("spine").firstOrNull()
        val spine = document.elements("itemref")
            .filter { it.attribute("linear")?.equals("no", ignoreCase = true) != true }
            .mapNotNull { it.attribute("idref")?.let(byId::get)?.path }
            .distinct()
            .take(EpubLimits.MAX_SPINE_ITEMS)

        if (spine.isEmpty()) {
            throw EpubFormatException(
                EpubFormatException.Kind.EMPTY_CONTENT,
                "这本 EPUB 没有可读的正文（spine 为空）",
            )
        }

        val ncxPath = spineElement?.attribute("toc")?.let(byId::get)?.path
            ?: manifest.firstOrNull { it.mediaType.equals(NCX_MEDIA_TYPE, ignoreCase = true) }?.path
        val navPath = manifest.firstOrNull { item ->
            item.properties.split(' ').any { it.equals("nav", ignoreCase = true) }
        }?.path

        return EpubPackageInfo(
            packagePath = packagePath,
            baseDir = baseDir,
            title = document.elements("title").firstOrNull()
                ?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            author = document.elements("creator").firstOrNull()
                ?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            spine = spine,
            ncxPath = ncxPath,
            navPath = navPath,
        )
    }

    private const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"
}
