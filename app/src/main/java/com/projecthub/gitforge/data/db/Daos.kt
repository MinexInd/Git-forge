package com.projecthub.gitforge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepositoryDao {

    @Query("SELECT * FROM repositories ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories ORDER BY lastOpenedAt DESC")
    suspend fun all(): List<RepositoryEntity>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun byId(id: Long): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE ownerName = :ownerName LIMIT 1")
    suspend fun byOwnerName(ownerName: String): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE ownerName = :ownerName LIMIT 1")
    fun observeByOwnerName(ownerName: String): Flow<RepositoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(repository: RepositoryEntity): Long

    @Query("UPDATE repositories SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)

    @Query("UPDATE repositories SET currentBranch = :branch WHERE id = :id")
    suspend fun updateBranch(id: Long, branch: String)

    @Query("UPDATE repositories SET sizeBytes = :bytes WHERE id = :id")
    suspend fun updateSize(id: Long, bytes: Long)

    @Query("DELETE FROM repositories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecentFileDao {

    @Query("SELECT * FROM recent_files WHERE repositoryId = :repositoryId ORDER BY openedAt DESC LIMIT :limit")
    suspend fun recent(repositoryId: Long, limit: Int = 20): List<RecentFileEntity>

    @Query("SELECT * FROM recent_files WHERE repositoryId = :repositoryId ORDER BY openedAt DESC LIMIT :limit")
    fun observeRecent(repositoryId: Long, limit: Int = 20): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: RecentFileEntity): Long

    @Query("DELETE FROM recent_files WHERE repositoryId = :repositoryId")
    suspend fun clearForRepository(repositoryId: Long)
}

@Dao
interface TerminalSessionDao {

    @Query("SELECT * FROM terminal_sessions ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<TerminalSessionEntity>>

    @Query("SELECT * FROM terminal_sessions ORDER BY lastUsedAt DESC")
    suspend fun all(): List<TerminalSessionEntity>

    @Query("SELECT * FROM terminal_sessions WHERE id = :id")
    suspend fun byId(id: Long): TerminalSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: TerminalSessionEntity): Long

    @Query("UPDATE terminal_sessions SET workingDirectory = :cwd, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateSession(id: Long, cwd: String, timestamp: Long)

    @Query("DELETE FROM terminal_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}
