package com.datgarscan.app.webapi

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object WebApiClient {

    const val SITE_URL = "https://datgarscanlation.xyz/"

    @Volatile private var instancia: DatGarApiService? = null
    @Volatile private var okHttpCompartido: OkHttpClient? = null
    @Volatile private var glideConfigurado = false

    fun get(): DatGarApiService {
        return instancia ?: synchronized(this) {
            instancia ?: crear().also { instancia = it }
        }
    }

    /** El mismo OkHttpClient que usa Retrofit, para que Glide comparta cookie y User-Agent. */
    fun okHttp(): OkHttpClient {
        return okHttpCompartido ?: synchronized(this) {
            okHttpCompartido ?: crearOkHttp().also { okHttpCompartido = it }
        }
    }

    /** Llamar una vez, antes de cargar cualquier imagen con Glide. */
    fun configurarGlide(context: android.content.Context) {
        if (glideConfigurado) return
        synchronized(this) {
            if (glideConfigurado) return
            glideConfigurado = true
            val factory = com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader.Factory(okHttp())
            com.bumptech.glide.Glide.get(context).registry
                .replace(com.bumptech.glide.load.model.GlideUrl::class.java, java.io.InputStream::class.java, factory)
        }
    }

    private fun crearOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val userAgent = ChallengeResolver.userAgentDetectado
            ?: "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        val headersInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Referer", SITE_URL)
                .header("User-Agent", userAgent)
            SesionManager.tokenEnMemoria?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }

        return OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar())
            .addInterceptor(headersInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun crear(): DatGarApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(SITE_URL)
            .client(okHttp())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(DatGarApiService::class.java)
    }
}
