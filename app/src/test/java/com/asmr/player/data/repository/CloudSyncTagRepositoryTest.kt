package com.asmr.player.data.repository

import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.data.remote.scraper.hasDlsiteWorkMetadata
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.AlbumTag
import com.asmr.player.domain.model.AlbumTagCodec
import com.asmr.player.domain.model.localizedTags
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class CloudSyncTagRepositoryTest {
    private val local = Album(title = "Local title", path = "/local", rjCode = "RJ01402323", tags = listOf("低语", "custom"))
    private val translated = AlbumTag("asmr-one:tag:1", "asmr-one", "低语", mapOf("en" to "Whispering", "zh-CN" to "低语"))

    @Test fun restrictedPageWithTitleIsNotSuccessfulMetadata() {
        val doc = Jsoup.parse("""<h1 id="work_name">Album</h1><div class="error_box error_box_work">You cannot buy this product from the country/region you live in</div>""")
        assertFalse(hasDlsiteWorkMetadata(doc))
        doc.body().append("""<table id="work_outline"><tr><td>Metadata</td></tr></table>""")
        assertTrue(hasDlsiteWorkMetadata(doc))
    }

    @Test fun missingDetailsStillEnrichExistingLocalTagsAndPreserveMetadata() = runBlocking {
        val repository = CloudSyncTagRepository({ listOf(translated) }, { 0L })
        val result = repository.enrich(DlsiteCloudSyncResolveResult.NotFound, local, "en_US") as DlsiteCloudSyncResolveResult.Success
        assertEquals(local.title, result.details.title)
        assertEquals(local.tags, result.details.tags)
        val stored = result.details.copy(tagDetails = AlbumTagCodec.decode(AlbumTagCodec.encode(result.details.tagDetails)))
        assertEquals(listOf("Whispering", "custom"), stored.localizedTags("en_US"))
        assertEquals(local.tags, stored.localizedTags("zh_CN"))
    }

    @Test fun batchSharesOneDictionaryRequestAcrossWorks() = runBlocking {
        var calls = 0
        val repository = CloudSyncTagRepository({ calls++; listOf(translated) }, { 0L })
        (1..20).map { index -> async {
            repository.enrich(DlsiteCloudSyncResolveResult.NotFound, local.copy(rjCode = "RJ014023$index"), "en_US")
        } }.awaitAll()
        assertEquals(1, calls)
    }

    @Test fun unavailableDictionaryDoesNotClaimSyncSuccessOrRepeatForEveryWork() = runBlocking {
        var calls = 0
        val repository = CloudSyncTagRepository({ calls++; error("offline") }, { 0L })
        repeat(3) {
            assertEquals(DlsiteCloudSyncResolveResult.NotFound,
                repository.enrich(DlsiteCloudSyncResolveResult.NotFound, local, "en_US"))
        }
        assertEquals(1, calls)
    }

    @Test fun existingOfficialTranslationIsNotReplacedByConflictingAlias() = runBlocking {
        var calls = 0
        val repository = CloudSyncTagRepository({ calls++; listOf(translated.copy(names = mapOf("en" to "Other"))) }, { 0L })
        val details = local.copy(tags = listOf("低语"), tagDetails = listOf(translated))
        val result = repository.enrich(DlsiteCloudSyncResolveResult.Success(local.rjCode, "en_US", details), local, "en_US") as DlsiteCloudSyncResolveResult.Success
        assertEquals(listOf("Whispering"), result.details.localizedTags("en_US"))
        assertEquals(0, calls)
    }
}
