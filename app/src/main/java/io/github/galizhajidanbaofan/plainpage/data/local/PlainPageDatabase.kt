package io.github.galizhajidanbaofan.plainpage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BookEntity::class], version = 1, exportSchema = true)
abstract class PlainPageDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        private const val NAME = "plainpage.db"

        fun build(context: Context): PlainPageDatabase =
            Room.databaseBuilder(context.applicationContext, PlainPageDatabase::class.java, NAME)
                // P0 阶段没有历史版本要迁移；真出问题宁可让用户重导，也不静默丢数据。
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
