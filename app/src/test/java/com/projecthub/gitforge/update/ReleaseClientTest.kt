package com.projecthub.gitforge.update

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real [ReleaseClient] against a real HTTP server, so the JSON parsing,
 * the pagination-shaped response handling, and the channel/version selection logic are
 * all covered by code that actually runs rather than by a stub.
 */
class ReleaseClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = ReleaseClient(
        owner = "ProjectHub-fie",
        repo = "Git-forge",
        baseUrl = server.url("/").toString().trimEnd('/'),
    )

    private val twoReleases = """
        [
          {
            "tag_name": "V1.1.0",
            "name": "GitForge 1.1.0",
            "body": "Adds in-app updates.",
            "html_url": "https://github.com/ProjectHub-fie/Git-forge/releases/tag/V1.1.0",
            "published_at": "2026-10-13T10:00:00Z",
            "assets": [
              {"name": "app-release.apk", "browser_download_url": "https://example.test/r.apk", "size": 1000},
              {"name": "app-debug.apk", "browser_download_url": "https://example.test/d.apk", "size": 2000}
            ]
          },
          {
            "tag_name": "V1.0.0",
            "name": "GitForge 1.0.0",
            "body": "First release.",
            "html_url": "https://github.com/ProjectHub-fie/Git-forge/releases/tag/V1.0.0",
            "published_at": "2026-10-10T10:00:00Z",
            "assets": [
              {"name": "app-release.apk", "browser_download_url": "https://example.test/old.apk", "size": 900}
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `parses releases and their assets`() = runTest {
        server.enqueue(MockResponse().setBody(twoReleases).setResponseCode(200))
        val releases = client().fetchReleases()
        assertEquals(2, releases.size)
        assertEquals("1.1.0", releases[0].version)
        assertEquals("V1.1.0", releases[0].tagName)
        assertEquals(2, releases[0].assets.size)
        assertEquals(2000L, releases[0].assets[1].sizeBytes)
    }

    @Test
    fun `finds the newest release newer than the installed version`() = runTest {
        server.enqueue(MockResponse().setBody(twoReleases).setResponseCode(200))
        val update = client().findUpdate(currentVersion = "1.0.0", channel = UpdateChannel.STABLE)
        assertNotNull(update)
        assertEquals("1.1.0", update!!.version)
    }

    @Test
    fun `returns null when the installed build is current`() = runTest {
        server.enqueue(MockResponse().setBody(twoReleases).setResponseCode(200))
        val update = client().findUpdate(currentVersion = "1.1.0", channel = UpdateChannel.STABLE)
        assertNull(update)
    }

    @Test
    fun `stable channel selects the release apk not the debug apk`() {
        val release = UpdateRelease(
            tagName = "V1.1.0",
            version = "1.1.0",
            name = "n",
            notes = "",
            htmlUrl = "u",
            publishedAt = "",
            assets = listOf(
                UpdateAsset("app-release.apk", "https://example.test/r.apk", 1),
                UpdateAsset("app-debug.apk", "https://example.test/d.apk", 2),
            ),
        )
        assertEquals("app-release.apk", release.assetFor(UpdateChannel.STABLE)?.name)
        assertEquals("app-debug.apk", release.assetFor(UpdateChannel.DEBUG)?.name)
    }

    @Test
    fun `debug builds follow the debug channel`() {
        assertEquals(UpdateChannel.DEBUG, UpdateChannel.forBuild(isDebug = true))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.forBuild(isDebug = false))
    }

    @Test
    fun `a release without a matching asset is skipped`() = runTest {
        val onlyDebug = """
            [{
              "tag_name": "V2.0.0",
              "name": "n",
              "html_url": "u",
              "assets": [{"name": "app-debug.apk", "browser_download_url": "x", "size": 1}]
            }]
        """.trimIndent()
        server.enqueue(MockResponse().setBody(onlyDebug).setResponseCode(200))
        val update = client().findUpdate(currentVersion = "1.0.0", channel = UpdateChannel.STABLE)
        assertNull(update)
    }

    @Test
    fun `the stable channel never falls back to a debug apk`() {
        // A release carrying only app-debug.apk must not be offered to a release
        // build: the keys differ, so the install would fail after the download.
        val release = UpdateRelease(
            tagName = "V2.0.0",
            version = "2.0.0",
            name = "n",
            notes = "",
            htmlUrl = "u",
            publishedAt = "",
            assets = listOf(UpdateAsset("app-debug.apk", "https://example.test/d.apk", 1)),
        )
        assertNull(release.assetFor(UpdateChannel.STABLE))
        assertNotNull(release.assetFor(UpdateChannel.DEBUG))
    }

    @Test
    fun `a differently named release apk is still accepted on the stable channel`() {
        val release = UpdateRelease(
            tagName = "V2.0.0",
            version = "2.0.0",
            name = "n",
            notes = "",
            htmlUrl = "u",
            publishedAt = "",
            assets = listOf(UpdateAsset("gitforge-2.0.0.apk", "https://example.test/g.apk", 1)),
        )
        assertEquals("gitforge-2.0.0.apk", release.assetFor(UpdateChannel.STABLE)?.name)
    }

    @Test
    fun `a rate limit is reported as a safe message not a crash`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"rate limit"}"""))
        val error = runCatching { client().fetchReleases() }.exceptionOrNull()
        assertTrue(error is UpdateException)
        assertTrue(error!!.message!!.contains("rate-limiting", ignoreCase = true))
    }

    @Test
    fun `a missing repository is explained rather than surfacing a raw status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val error = runCatching { client().fetchReleases() }.exceptionOrNull()
        assertTrue(error is UpdateException)
        assertTrue(error!!.message!!.contains("No releases"))
    }

    @Test
    fun `no token is sent when the app is signed out`() = runTest {
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        client().fetchReleases(token = null)
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `a token is attached as a bearer header and never in the url`() = runTest {
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        client().fetchReleases(token = "secret-token-value")
        val recorded = server.takeRequest()
        assertEquals("Bearer secret-token-value", recorded.getHeader("Authorization"))
        assertTrue(!recorded.path!!.contains("secret-token-value"))
    }
}
