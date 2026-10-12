package com.projecthub.gitforge.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A repository the user has cloned or added as a local workspace.
 *
 * [localPath] is a relative folder name inside the app-private workspace root, not
 * an absolute path, so the database stays valid if Android relocates the app data
 * directory between installs or after a restore.
 */
@Entity(
    tableName = "repositories",
    indices = [Index(value = ["ownerName"], unique = true), Index(value = ["lastOpenedAt"])],
)
data class RepositoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** `owner/name` for GitHub repositories, or a bare display name for local ones. */
    val ownerName: String,
    val displayName: String,
    val localPath: String,
    val remoteUrl: String?,
    val defaultBranch: String,
    val currentBranch: String,
    val isLocalOnly: Boolean,
    val lastOpenedAt: Long,
    val createdAt: Long,
    val sizeBytes: Long = 0,
)

/** A file the user has opened recently, used to restore editor tabs. */
@Entity(
    tableName = "recent_files",
    indices = [Index(value = ["repositoryId", "relativePath"], unique = true)],
)
data class RecentFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repositoryId: Long,
    val relativePath: String,
    val openedAt: Long,
)

/** A named terminal session the user created, restored across app restarts. */
@Entity(tableName = "terminal_sessions")
data class TerminalSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val workingDirectory: String,
    val repositoryId: Long?,
    val createdAt: Long,
    val lastUsedAt: Long,
)
