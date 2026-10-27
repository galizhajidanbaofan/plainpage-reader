package io.github.galizhajidanbaofan.plainpage.domain.repository

import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import kotlinx.coroutines.flow.Flow

/**
 * 书架的持久化边界。
 *
 * 上层（UI / ViewModel）只依赖这个接口，不关心底层是 Room 还是别的存储；
 * 换存储实现时要保证这里的行为约定不变：
 * - 读接口返回的是**新快照**（不可变 List），调用方可自由持有；
 * - 写操作都是挂起函数，且必须在后台线程完成（实现方负责 withContext）。
 */
interface BookRepository {

    /** 书架全量，按「最近打开优先、从未打开按导入时间倒序」排好。 */
    fun observeShelf(): Flow<List<Book>>

    /** 最近阅读，只含打开过的书，按最近打开时间倒序。 */
    fun observeRecent(limit: Int = 20): Flow<List<Book>>

    suspend fun find(id: Long): Book?

    /** 单本书的响应式读取（阅读页用）。 */
    fun observeBook(id: Long): Flow<Book?>

    /** 新增书籍，返回自增主键。 */
    suspend fun insert(book: Book): Long

    /** 只删记录；私有目录里的文件由导入层决定是否清理。 */
    suspend fun delete(id: Long)

    suspend fun markOpened(id: Long, atMillis: Long)

    /** 保存阅读进度；[location] 为 null 表示清除进度。 */
    suspend fun saveLocation(id: Long, location: ReadingLocation?)

    /** 记录封面缓存文件的绝对路径；null 表示没有封面。 */
    suspend fun setCover(id: Long, coverPath: String?)
}
