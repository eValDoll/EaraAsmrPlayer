package com.asmr.player.subtitle

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class GeneratedSubtitleFileAccessTest {
    @Test
    fun externallyEditedSubtitleIsPreservedAndDeletedAudioIsNotRecreated() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val folder = Files.createTempDirectory(context.cacheDir.toPath(), "subtitle-guard").toFile()
        try {
            val audio = File(folder, "voice.wav").apply { writeText("音频", Charsets.UTF_8) }
            val output = File(folder, "voice.lrc").apply { writeText("原文", Charsets.UTF_8) }
            val album = database.albumDao().insertAlbum(AlbumEntity(title = "作品", path = folder.path))
            val track = database.trackDao().insertTrack(TrackEntity(albumId = album, title = "音轨", path = audio.path))
            database.trackDao().insertSubtitles(listOf(SubtitleEntity(trackId = track, startMs = 0, endMs = 1000, text = "译文")))
            val exporter = GeneratedSubtitleFileExporter(context, database)
            val beforeTranslation = checkNotNull(exporter.captureOutput(track))
            output.writeText("用户已经修改的字幕", Charsets.UTF_8)
            assertTrue(runCatching { exporter.export(track, expectedOutput = beforeTranslation) }.isFailure)
            assertEquals("用户已经修改的字幕", output.readText(Charsets.UTF_8))

            output.delete()
            audio.delete()
            assertTrue(runCatching { exporter.export(track) }.isFailure)
            assertFalse(output.exists())
            assertFalse(audio.exists())
        } finally {
            database.close()
            folder.deleteRecursively()
        }
    }
}
