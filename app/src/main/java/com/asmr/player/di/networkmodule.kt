package com.asmr.player.di

import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.api.Asmr200Api
import com.asmr.player.data.remote.api.Asmr100Api
import com.asmr.player.data.remote.api.Asmr300Api
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import javax.inject.Named

import com.asmr.player.data.remote.TrafficStatsInterceptor
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.util.MessageManager
import com.asmr.player.util.DlsiteAntiHotlink
import java.io.IOException

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        messageManager: MessageManager,
        trafficStatsInterceptor: TrafficStatsInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val asmrHeaders = Interceptor { chain ->
            val request = chain.request()
            val host = request.url.host.lowercase()
            val silentIoError = request.header(NetworkHeaders.HEADER_SILENT_IO_ERROR) == NetworkHeaders.SILENT_IO_ERROR_ON
            
            val builder = request.newBuilder()
                .header("User-Agent", NetworkHeaders.USER_AGENT)

            if (silentIoError) {
                builder.removeHeader(NetworkHeaders.HEADER_SILENT_IO_ERROR)
            }

            if (host.contains("asmr.one")) {
                builder.header("Origin", "https://www.asmr.one")
                builder.header("Referer", "https://www.asmr.one/")
            } else if (host.contains("asmr-100.com") || host.contains("asmr-200.com") || host.contains("asmr-300.com")) {
                builder.header("Origin", "https://www.asmr.one")
                builder.header("Referer", "https://www.asmr.one/")
            } else if (host.contains("dlsite.")) {
                builder.header("Referer", "https://www.dlsite.com/")
            } else if (host.contains("byteair.volces.com")) {
                builder.header("Referer", "https://www.dlsite.com/")
            }
            
            try {
                val response = chain.proceed(builder.build())
                if (!response.isSuccessful) {
                    when (response.code) {
                        401 -> messageManager.showError("认证已过期，请重新登录")
                        403 -> messageManager.showError("访问被拒绝")
                        404 -> {} // 忽略 404
                        500, 502, 503, 504 -> messageManager.showError("服务器开小差了，请稍后重试")
                        else -> {}
                    }
                }
                response
            } catch (e: IOException) {
                val canceled = runCatching { chain.call().isCanceled() }.getOrDefault(false)
                val canceledByMessage = e.message?.contains("canceled", ignoreCase = true) == true
                if (!silentIoError && !canceled && !canceledByMessage) {
                    messageManager.showError("网络连接失败，请检查网络")
                }
                throw e
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(asmrHeaders)
            .addInterceptor(trafficStatsInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("image")
    fun provideImageOkHttpClient(
        trafficStatsInterceptor: TrafficStatsInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val headers = Interceptor { chain ->
            val request = chain.request()
            val host = request.url.host.lowercase()

            val builder = request.newBuilder()
                .header("User-Agent", NetworkHeaders.USER_AGENT)

            if (host.contains("asmr.one")) {
                builder.header("Origin", "https://www.asmr.one")
                builder.header("Referer", "https://www.asmr.one/")
            } else if (host.contains("asmr-100.com") || host.contains("asmr-200.com") || host.contains("asmr-300.com")) {
                builder.header("Origin", "https://www.asmr.one")
                builder.header("Referer", "https://www.asmr.one/")
            } else {
                val dlsiteHeaders = DlsiteAntiHotlink.headersForImageUrl(request.url.toString())
                dlsiteHeaders.forEach { (k, v) ->
                    if (request.header(k) == null) builder.header(k, v)
                }
            }

            chain.proceed(builder.build())
        }

        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 2
        }

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(headers)
            .addInterceptor(trafficStatsInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(AsmrOneApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAsmrOneApi(retrofit: Retrofit): AsmrOneApi {
        return retrofit.create(AsmrOneApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAsmr200Api(okHttpClient: OkHttpClient): Asmr200Api {
        val retrofit = Retrofit.Builder()
            .baseUrl(Asmr200Api.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(Asmr200Api::class.java)
    }

    @Provides
    @Singleton
    fun provideAsmr100Api(okHttpClient: OkHttpClient): Asmr100Api {
        val retrofit = Retrofit.Builder()
            .baseUrl(Asmr100Api.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(Asmr100Api::class.java)
    }

    @Provides
    @Singleton
    fun provideAsmr300Api(okHttpClient: OkHttpClient): Asmr300Api {
        val retrofit = Retrofit.Builder()
            .baseUrl(Asmr300Api.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(Asmr300Api::class.java)
    }
}
