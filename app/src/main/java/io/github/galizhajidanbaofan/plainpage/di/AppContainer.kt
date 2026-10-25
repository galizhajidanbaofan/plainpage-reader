package io.github.galizhajidanbaofan.plainpage.di

import android.content.Context
import io.github.galizhajidanbaofan.plainpage.data.importing.BookImporter
import io.github.galizhajidanbaofan.plainpage.data.importing.booksDirectory
import io.github.galizhajidanbaofan.plainpage.data.local.PlainPageDatabase
import io.github.galizhajidanbaofan.plainpage.data.repository.RoomBookRepository
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.txt.TxtReaderEngine
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.domain.repository.BookRepository

/**
 * 手写组合根。
 *
 * 为什么不上 Hilt/Koin：P0 的依赖图只有「数据库 → 仓库 → 导入器」这几个对象，
 * 引入 DI 框架的收益（编译期注解放任、作用域管理）远小于代价（KSP 复杂度、启动成本、可读性）。
 * 等依赖图真的复杂起来再迁移，接口边界（BookRepository）已经留好了。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database: PlainPageDatabase by lazy { PlainPageDatabase.build(appContext) }

    val bookRepository: BookRepository by lazy { RoomBookRepository(database.bookDao()) }

    /**
     * 按格式造引擎。还没有实现的格式返回 null，由 UI 显示「还没接入」而不是假装打开成功。
     *
     * 进度写回在这里注入：引擎不认识 Room，只把位置交给这个 lambda。
     */
    fun createEngine(book: Book): ReaderEngine? = when (book.format) {
        BookFormat.TXT -> TxtReaderEngine(
            progressSink = { location, _ -> bookRepository.saveLocation(book.id, location) },
        )
        // TODO(阶段 3)：EPUB 自研解析、PDF 用 Android PdfRenderer。
        BookFormat.EPUB, BookFormat.PDF -> null
    }

    val bookImporter: BookImporter by lazy {
        BookImporter(
            contentResolver = appContext.contentResolver,
            booksDir = booksDirectory(appContext.filesDir),
        )
    }
}
