package com.projecthub.gitforge.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    val id: Long = 0,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0,
    @SerialName("total_private_repos") val totalPrivateRepos: Int? = null,
)

@Serializable
data class GitHubRepository(
    val id: Long = 0,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    val description: String? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val fork: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("clone_url") val cloneUrl: String? = null,
    @SerialName("ssh_url") val sshUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val size: Long = 0,
    val owner: GitHubOwner? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
) {
    val ownerLogin: String get() = owner?.login ?: fullName.substringBefore('/')
}

@Serializable
data class GitHubOwner(
    val login: String,
    val avatarUrl: String? = null,
)

@Serializable
data class GitHubBranch(
    val name: String,
    val commit: GitHubCommitRef? = null,
    val protected: Boolean = false,
)

@Serializable
data class GitHubCommitRef(val sha: String, val url: String? = null)

@Serializable
data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0,
    val type: String,
    @SerialName("download_url") val downloadUrl: String? = null,
    val content: String? = null,
    val encoding: String? = null,
)

@Serializable
data class GitHubRateLimit(
    val limit: Int,
    val remaining: Int,
    val reset: Long,
)

/** Parsed from the `Link` response header for pagination. */
data class PageLinks(
    val next: Int?,
    val last: Int?,
    val first: Int?,
    val prev: Int?,
)

/** A page of results plus pagination metadata, so the UI can page deterministically. */
data class Paged<T>(
    val items: List<T>,
    val page: Int,
    val nextPage: Int?,
    val lastPage: Int?,
    val hasMore: Boolean,
)
