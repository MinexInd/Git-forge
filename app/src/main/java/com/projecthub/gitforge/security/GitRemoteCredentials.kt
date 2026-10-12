package com.projecthub.gitforge.security

/**
 * Builds credential-bearing HTTPS URLs for Git remotes.
 *
 * The token is placed in the URL only for the duration of a single network call
 * and is never persisted: the remote stored on disk keeps the bare URL. Every
 * caller must pass the result straight to JGit and must not log it.
 */
object GitRemoteCredentials {

    /**
     * Injects [token] as the password for GitHub HTTPS remotes.
     * Returns the original URL unchanged for non-GitHub hosts and for URLs that
     * already carry userinfo.
     */
    fun withToken(remoteUrl: String, token: String?): String {
        if (token.isNullOrBlank()) return remoteUrl
        if (!remoteUrl.startsWith("https://", ignoreCase = true)) return remoteUrl
        if (!isGitHubHost(remoteUrl)) return remoteUrl
        if (hasUserInfo(remoteUrl)) return remoteUrl
        return remoteUrl.replaceFirst("https://", "https://x-access-token:$token@")
    }

    /** Removes any userinfo so a URL is safe to display, log or persist. */
    fun stripCredentials(remoteUrl: String): String {
        val withoutUserInfo = remoteUrl.replace(
            Regex("""^(https?://)[^/@\s]+@"""),
            "$1",
        )
        return withoutUserInfo.replace(
            Regex("""(?i)\b(access_token|token|password)=[^&\s]+"""),
            "$1=***",
        )
    }

    private fun isGitHubHost(url: String): Boolean {
        val host = url.removePrefix("https://").substringBefore('/')
        return host.equals("github.com", ignoreCase = true) ||
            host.equals("www.github.com", ignoreCase = true)
    }

    private fun hasUserInfo(url: String): Boolean {
        val authority = url.removePrefix("https://").substringBefore('/')
        return authority.contains('@')
    }
}
