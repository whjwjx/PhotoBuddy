package com.example.photoorganizer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaStatusEntity::class,
        AlbumEntity::class,
        AlbumItemEntity::class,
        MediaIndexEntity::class,
        UserActionLogEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaStatusDao(): MediaStatusDao

    abstract fun albumDao(): AlbumDao

    abstract fun mediaIndexDao(): MediaIndexDao

    abstract fun userActionLogDao(): UserActionLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "photo_organizer.db",
                    )
                    // MVP 阶段本地状态与索引均可从 MediaStore 重建，升级时直接重建。
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
