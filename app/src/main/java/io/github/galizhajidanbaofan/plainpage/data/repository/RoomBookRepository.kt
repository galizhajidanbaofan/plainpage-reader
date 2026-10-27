package io.github.galizhajidanbaofan.plainpage.data.repository

import io.github.galizhajidanbaofan.plainpage.data.local.BookDao
import io.github.galizhajidanbaofan.plainpage.data.local.toDomain
import io.github.galizhajidanbaofan.plainpage.data.local.toEntity
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocationCodec
import io.github.galizhajidanbaofan.plainpage.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 实现。所有映射都走 `toDomain()`：遇到无法识别的格式名会返回 null 并被过滤掉，
 * 这样一条脏数据最多让一本书消失，而不是让整个书架崩掉。
 */
class RoomBookRepository(private val dao: BookDao) : BookRepository {

    override fun observeShelf(): Flow<List<Book>> =
        dao.observeShelf().map { entities -> entities.mapNotNull { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<Book>> =
        dao.observeRecent(limit).map { entities -> entities.mapNotNull { it.toDomain() } }

    override suspend fun find(id: Long): Book? = dao.findById(id)?.toDomain()

    override fun observeBook(id: Long): Flow<Book?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun insert(book: Book): Long = dao.insert(book.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun markOpened(id: Long, atMillis: Long) = dao.updateLastOpened(id, atMillis)

    override suspend fun saveLocation(id: Long, location: ReadingLocation?) =
        dao.updateReadingLocation(id, location?.let(ReadingLocationCodec::encode))

    override suspend fun setCover(id: Long, coverPath: String?) = dao.updateCover(id, coverPath)
}
