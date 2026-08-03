package com.datgarscan.app.webapi

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Comparte las cookies entre el WebView (que resuelve el desafío anti-bot
 * de InfinityFree) y OkHttp/Retrofit (que hace las llamadas reales a la API).
 * Mismo mecanismo que ya usamos y probamos en FoxGod Scan.
 */
class WebViewCookieJar : CookieJar {

    private val manager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            manager.setCookie(url.toString(), cookie.toString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookiesString = manager.getCookie(url.toString()) ?: return emptyList()
        return cookiesString.split(";").mapNotNull { par ->
            Cookie.parse(url, par.trim())
        }
    }
}
