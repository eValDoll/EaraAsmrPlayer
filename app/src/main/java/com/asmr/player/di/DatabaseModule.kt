package com.asmr.player.di
import android.content.Context
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.AlbumGroupDao
import com.asmr.player.data.local.db.dao.AlbumGroupItemDao
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.local.db.dao.PlaylistDao
import com.asmr.player.data.local.db.dao.PlaylistItemDao
import com.asmr.player.data.local.db.dao.RemoteSubtitleSourceDao
import com.asmr.player.data.local.db.dao.TrackSliceDao
import com.asmr.player.data.local.db.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabaseProvider.get(context)
    }

    @Provides
    fun provideAlbumDao(database: AppDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideTrackDao(database: AppDatabase): TrackDao = database.trackDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providePlaylistItemDao(database: AppDatabase): PlaylistItemDao = database.playlistItemDao()

    @Provides
    fun provideAlbumGroupDao(database: AppDatabase): AlbumGroupDao = database.albumGroupDao()

    @Provides
    fun provideAlbumGroupItemDao(database: AppDatabase): AlbumGroupItemDao = database.albumGroupItemDao()

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideRemoteSubtitleSourceDao(database: AppDatabase): RemoteSubtitleSourceDao = database.remoteSubtitleSourceDao()

    @Provides
    fun provideTrackSliceDao(database: AppDatabase): TrackSliceDao = database.trackSliceDao()
}
