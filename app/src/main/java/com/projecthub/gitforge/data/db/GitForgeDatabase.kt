package com.projecthub.gitforge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local metadata only: repository bookkeeping, recently opened files and terminal
 * session descriptions. File contents and Git objects live on disk, never in the
 * database, so the app keeps working when the database is reset.
 */
@Database(
    entities = [RepositoryEntity::class, RecentFileEntity::class, TerminalSessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class GitForgeDatabase : RoomDatabase() {

    abstract fun repositoryDao(): RepositoryDao

    abstract fun recentFileDao(): RecentFileDao

    abstract fun terminalSessionDao(): TerminalSessionDao

    companion object {
        const val NAME = "gitforge.db"

        fun create(context: Context, name: String = NAME): GitForgeDatabase =
            Room.databaseBuilder(context.applicationContext, GitForgeDatabase::class.java, name)
                .fallbackToDestructiveMigration()
                .build()
    }
}
