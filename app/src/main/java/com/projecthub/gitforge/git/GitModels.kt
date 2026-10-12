package com.projecthub.gitforge.git

/** Working-tree state of a single path, derived from a JGit Status. */
enum class ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    UNTRACKED,
    CONFLICTING,
    UNKNOWN,
}

data class FileStatus(
    val relativePath: String,
    val changeType: ChangeType,
    /** True when the change is already in the index (staged for commit). */
    val staged: Boolean,
) {
    val statusLabel: String
        get() = when {
            changeType == ChangeType.UNTRACKED -> "??"
            staged && changeType == ChangeType.ADDED -> "A "
            staged && changeType == ChangeType.MODIFIED -> "M "
            staged && changeType == ChangeType.DELETED -> "D "
            changeType == ChangeType.MODIFIED -> " M"
            changeType == ChangeType.DELETED -> " D"
            changeType == ChangeType.CONFLICTING -> "UU"
            else -> "??"
        }
}

data class RepoStatus(
    val branch: String,
    val files: List<FileStatus>,
    val aheadCount: Int,
    val behindCount: Int,
    val hasUncommittedChanges: Boolean,
) {
    val staged: List<FileStatus> get() = files.filter { it.staged }
    val unstaged: List<FileStatus> get() = files.filter { !it.staged && it.changeType != ChangeType.UNTRACKED }
    val untracked: List<FileStatus> get() = files.filter { it.changeType == ChangeType.UNTRACKED }
    val conflicting: List<FileStatus> get() = files.filter { it.changeType == ChangeType.CONFLICTING }
}

data class CommitInfo(
    val sha: String,
    val shortSha: String,
    val message: String,
    val authorName: String,
    val authorEmail: String,
    val timestampSeconds: Long,
    val parentShas: List<String>,
)

data class BranchInfo(
    val name: String,
    val isCurrent: Boolean,
    val isRemote: Boolean,
    val lastCommitSha: String?,
)

data class RemoteInfo(
    val name: String,
    val fetchUrl: String,
    val pushUrl: String,
)

data class DiffLine(
    val kind: Kind,
    val text: String,
) {
    enum class Kind { CONTEXT, ADDED, REMOVED, HEADER, HUNK }
}

data class FileDiff(
    val relativePath: String,
    val oldPath: String?,
    val newPath: String?,
    val changeType: ChangeType,
    val lines: List<DiffLine>,
    val additions: Int,
    val deletions: Int,
)

data class CloneProgress(
    val phase: String,
    val completed: Int,
    val total: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
}

/** Result of a merge attempt, surfaced so the UI can explain conflicts. */
data class MergeOutcome(
    val status: MergeStatus,
    val conflictingPaths: List<String>,
    val message: String,
)

enum class MergeStatus { ALREADY_UP_TO_DATE, FAST_FORWARD, MERGED, CONFLICTING, FAILED }

/** Outcome of a push, including whether the remote actually accepted the update. */
data class PushOutcome(
    val remoteName: String,
    val updatedRefs: List<String>,
    val rejectedRefs: List<String>,
    val messages: List<String>,
)
