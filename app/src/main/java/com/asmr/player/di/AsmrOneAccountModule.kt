package com.asmr.player.di

import android.content.Context
import androidx.room.Room
import com.asmr.player.data.local.db.AsmrOneCloudDatabase
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.NetworkRouteManager
import com.asmr.player.data.remote.TrafficStatsInterceptor
import com.asmr.player.data.remote.api.AsmrOneAccountApi
import com.asmr.player.data.remote.api.AsmrOneAccountApiProvider
import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.data.settings.SettingsRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AsmrOneAccountModule {
    @Provides
    fun credentials(store: com.asmr.player.data.remote.auth.AsmrOneAuthStore): com.asmr.player.data.remote.auth.AsmrOneCredentialStorage = store

    @Provides
    fun scheduler(scheduler: com.asmr.player.work.AsmrOneSyncScheduler): com.asmr.player.work.AsmrOneSyncScheduling = scheduler

    @Provides
    @Singleton
    fun cloudDatabase(@ApplicationContext context: Context): AsmrOneCloudDatabase =
        Room.databaseBuilder(context, AsmrOneCloudDatabase::class.java, "asmr_one_cloud.db").build()

    @Provides
    @Singleton
    fun accountApiProvider(settings: SettingsRepository, routes: NetworkRouteManager,
        traffic: TrafficStatsInterceptor, gson: Gson): AsmrOneAccountApiProvider {
        val client = routes.register(OkHttpClient.Builder()
            .proxySelector(routes.proxySelector).proxyAuthenticator(routes.proxyAuthenticator).dns(routes.dns)
            .dispatcher(Dispatcher().apply { maxRequests = 3; maxRequestsPerHost = 2 })
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url
                check(url.isHttps && url.host in ACCOUNT_HOSTS && url.encodedPath.startsWith("/api/"))
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", NetworkHeaders.USER_AGENT)
                    .header("Origin", "https://asmr.one").header("Referer", "https://asmr.one/").build())
            }.addInterceptor(traffic).build())
        val apis = mutableMapOf<String, AsmrOneAccountApi>()
        return AsmrOneAccountApiProvider {
            val base = AsmrOneEndpoint.directBaseUrl(settings.asmrOneSite.first())
                ?: "https://api.asmr-200.com/api/"
            synchronized(apis) {
                apis.getOrPut(base) { Retrofit.Builder().baseUrl(base).client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson)).build().create(AsmrOneAccountApi::class.java) }
            }
        }
    }
}

internal val ACCOUNT_HOSTS = setOf("api.asmr.one", "api.asmr-100.com", "api.asmr-200.com", "api.asmr-300.com")
