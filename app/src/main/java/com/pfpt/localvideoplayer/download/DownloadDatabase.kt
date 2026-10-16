package com.pfpt.localvideoplayer.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DownloadTaskEntity::class], version = 2, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN hlsTotalSegments INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN downloadSpeedBps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN isMerging INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: DownloadDatabase? = null

        fun get(context: Context): DownloadDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DownloadDatabase::class.java,
                "downloads.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
