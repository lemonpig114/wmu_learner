package com.wmu.keban.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 应用数据库(版本 1)
 * 注意:第二阶段新增课程表实体时将升级 version 并提供迁移,
 * 不得使用破坏性迁移以免丢失用户已有数据。
 */
@Database(
    entities = [TaskItem::class, Setting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "keban.db"
                ).build().also { instance = it }
            }
    }
}
