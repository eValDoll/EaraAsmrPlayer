package com.asmr.player.ui.library

import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncSelectionCoverSupportTest {
    @Test
    fun resolveCoverSources_usesCanonicalWhenCoverUrlBlank() {
        val candidate = candidate(workno = "RJ123456", coverUrl = "")

        val result = resolveCloudSyncCandidateCoverSources(candidate)

        assertEquals(
            listOf(
                CloudSyncCandidateCoverSource(
                    label = "canonical",
                    url = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ124000/RJ123456_img_main.jpg"
                )
            ),
            result
        )
    }

    @Test
    fun resolveCoverSources_normalizesProtocolRelativeAndKeepsCanonicalFirst() {
        val candidate = candidate(
            workno = "RJ123456",
            coverUrl = "//img.dlsite.jp/modpub/images2/work/doujin/RJ124000/RJ123456_img_sam.jpg"
        )

        val result = resolveCloudSyncCandidateCoverSources(candidate)

        assertEquals(
            listOf(
                CloudSyncCandidateCoverSource(
                    label = "canonical",
                    url = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ124000/RJ123456_img_main.jpg"
                ),
                CloudSyncCandidateCoverSource(
                    label = "search",
                    url = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ124000/RJ123456_img_sam.jpg"
                )
            ),
            result
        )
    }

    @Test
    fun resolveCoverSources_deduplicatesWhenRawMatchesCanonical() {
        val candidate = candidate(
            workno = "RJ123456",
            coverUrl = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ124000/RJ123456_img_main.jpg"
        )

        val result = resolveCloudSyncCandidateCoverSources(candidate)

        assertEquals(
            listOf(
                CloudSyncCandidateCoverSource(
                    label = "canonical",
                    url = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ124000/RJ123456_img_main.jpg"
                )
            ),
            result
        )
    }

    @Test
    fun resolveCoverSources_fallsBackToNormalizedRawWhenCanonicalUnavailable() {
        val candidate = candidate(
            workno = "INVALID",
            coverUrl = "/images2/work/pro/fallback/sample.jpg"
        )

        val result = resolveCloudSyncCandidateCoverSources(candidate)

        assertEquals(
            listOf(
                CloudSyncCandidateCoverSource(
                    label = "search",
                    url = "https://img.dlsite.jp/images2/work/pro/fallback/sample.jpg"
                )
            ),
            result
        )
    }

    @Test
    fun resolveCoverSources_roundsCanonicalFolderUpToNextThousand() {
        val candidate = candidate(workno = "RJ01524112", coverUrl = "")

        val result = resolveCloudSyncCandidateCoverSources(candidate)

        assertEquals(
            listOf(
                CloudSyncCandidateCoverSource(
                    label = "canonical",
                    url = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01525000/RJ01524112_img_main.jpg"
                )
            ),
            result
        )
    }

    private fun candidate(workno: String, coverUrl: String): DlsiteCloudSyncCandidate {
        return DlsiteCloudSyncCandidate(
            workno = workno,
            title = "Title",
            cv = "CV",
            coverUrl = coverUrl
        )
    }
}
