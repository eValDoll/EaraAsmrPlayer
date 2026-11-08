package com.asmr.player.data.remote.update

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateClientTest {
    @Test
    fun `four part hotfix version upgrades the previous release`() {
        val client = GitHubUpdateClient(OkHttpClient(), supportedAbis = emptyList())

        assertTrue(client.isNewerThanCurrent("v1.2.5.1", "1.2.5"))
        assertFalse(client.isNewerThanCurrent("1.2.5", "1.2.5.1"))
        assertFalse(client.isNewerThanCurrent("1.2.5.1", "1.2.5.1"))
    }

    @Test
    fun `next patch version upgrades the hotfix release`() {
        val client = GitHubUpdateClient(OkHttpClient(), supportedAbis = emptyList())

        assertTrue(client.isNewerThanCurrent("1.2.6", "1.2.5.1"))
        assertFalse(client.isNewerThanCurrent("1.2.5.1", "1.2.6"))
    }

    @Test
    fun `prefers the device ABI before universal`() {
        val assets = listOf(
            asset("AsmrPlayer-v1.2.1-universal.apk"),
            asset("AsmrPlayer-v1.2.1-arm64-v8a.apk")
        )

        val selected = pickApkAssetForDevice(assets, listOf("arm64-v8a"))

        assertEquals("AsmrPlayer-v1.2.1-arm64-v8a.apk", selected?.name)
    }

    @Test
    fun `falls back to the universal release artifact`() {
        val assets = listOf(asset("AsmrPlayer-v1.2.0-universal.apk"))

        val selected = pickApkAssetForDevice(assets, listOf("arm64-v8a"))

        assertEquals("AsmrPlayer-v1.2.0-universal.apk", selected?.name)
    }

    @Test
    fun `x86 does not select x86_64`() {
        val assets = listOf(
            asset("AsmrPlayer-v1.2.1-x86_64.apk"),
            asset("AsmrPlayer-v1.2.1-x86.apk")
        )

        val selected = pickApkAssetForDevice(assets, listOf("x86"))

        assertEquals("AsmrPlayer-v1.2.1-x86.apk", selected?.name)
    }

    @Test
    fun `v1_1_6 legacy picker accepts the new single universal APK`() {
        val assets = listOf(asset("AsmrPlayer-v1.2.0-universal.apk"))

        val selected = pickApkAssetForLegacyClient(assets)

        assertEquals("AsmrPlayer-v1.2.0-universal.apk", selected?.name)
    }

    private fun asset(name: String) = GitHubReleaseAsset(
        name = name,
        browserDownloadUrl = "https://example.com/$name"
    )

    private fun pickApkAssetForLegacyClient(
        assets: List<GitHubReleaseAsset>
    ): GitHubReleaseAsset? {
        val apks = assets.filter { it.name.orEmpty().lowercase().endsWith(".apk") }
        return (
            apks.firstOrNull { it.name.orEmpty().contains("universal", ignoreCase = true) }
                ?: apks.firstOrNull { it.name.orEmpty().contains("arm64", ignoreCase = true) }
                ?: apks.firstOrNull { it.name.orEmpty().contains("debug", ignoreCase = true) }
                ?: apks.firstOrNull()
            )?.takeIf {
            it.browserDownloadUrl.orEmpty().startsWith("http", ignoreCase = true)
        }
    }
}
