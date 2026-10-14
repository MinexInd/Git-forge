package com.projecthub.gitforge.update

import com.projecthub.gitforge.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A release lookup failure whose message is safe to show to the user. */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads published releases from the GitHub REST API.
 *
 * Only the releases endpoint is used, and it needs no special scope: public
 * repositories answer unauthenticated. A token is optional and is passed in per call
 * rather than held, so a signed-out app still checks for updates and the client never
 * stores a credential. The token is never logged.
 */
class ReleaseClient(
    private val owner: String,
    private val repo: String,
    private val baseUrl: String = "https://api.github.com",
    client: OkHttpClient? = null,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** All published releases, newest first, as GitHub returns them. */
    suspend fun fetchReleases(token: String? = null): List<UpdateRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/repos/$owner/$repo/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .get()
            .build()

        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string().orEmpty()
                        val parsed = json.decodeFromString(ListSerializer, body)
                        parsed.map { it.toUpdateRelease() }
                    }

                    response.code == 404 ->
                        throw UpdateException("No releases are published for $owner/$repo yet.")

                    response.code == 403 || response.code == 429 ->
                        throw UpdateException("GitHub is rate-limiting the update check. Try again later.")

                    else ->
                        throw UpdateException("GitHub returned HTTP ${response.code} for the update check.")
                }
            }
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            AppLog.w("Update check could not reach GitHub", e)
            throw UpdateException("Could not reach GitHub. Check your connection and try again.", e)
        } catch (e: Exception) {
            AppLog.w("Update check failed to parse the response", e)
            throw UpdateException("GitHub's response could not be read.", e)
        }
    }

    /**
     * The newest release on [channel] that is newer than [currentVersion], or null
     * when the installed build is already current.
     */
    suspend fun findUpdate(
        currentVersion: String,
        channel: UpdateChannel,
        token: String? = null,
    ): UpdateRelease? = fetchReleases(token)
        .asSequence()
        .filter { VersionComparator.isNewer(it.version, currentVersion) }
        .filter { it.assetFor(channel) != null }
        .maxByOrNull { VersionComparator.parse(it.version).fold(0L) { acc, n -> acc * 1000 + n } }

    private fun ReleaseDto.toUpdateRelease() = UpdateRelease(
        tagName = tagName,
        version = VersionComparator.parse(tagName).joinToString(".").ifEmpty { tagName },
        name = name.ifBlank { tagName },
        notes = body.orEmpty(),
        htmlUrl = htmlUrl,
        publishedAt = publishedAt.orEmpty(),
        assets = assets.orEmpty().map {
            UpdateAsset(name = it.name, url = it.browserDownloadUrl, sizeBytes = it.size, sha256 = it.sha256())
        },
    )

    // ------------------------------------------------------------------ wire types

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String,
        val name: String = "",
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<AssetDto>? = null,
    )

    @Serializable
    private data class AssetDto(
        val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
        val size: Long = 0,
        /** Present on newer API responses as `sha256:<hex>`. Optional. */
        val digest: String? = null,
    ) {
        fun sha256(): String? = digest?.substringAfter("sha256:", "")?.lowercase()?.takeIf { it.length == 64 }
    }

    private companion object {
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(ReleaseDto.serializer())
    }
}
