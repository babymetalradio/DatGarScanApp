package com.datgarscan.app.webapi

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient

object ChallengeResolver {

    @Volatile var resuelto = false
        private set

    var userAgentDetectado: String? = null
        private set

    private val callbacksEsperando = mutableListOf<() -> Unit>()

    fun ejecutar(webView: WebView, onListo: () -> Unit) {
        if (resuelto) {
            onListo()
            return
        }
        callbacksEsperando.add(onListo)
        if (callbacksEsperando.size > 1) return

        configurarWebView(webView)
        userAgentDetectado = webView.settings.userAgentString
        webView.loadUrl(WebApiClient.SITE_URL)

        // Respaldo por si la deteccion falla. El hosting nuevo (BanaHosting) no
        // tiene el desafio anti-bot que si tenia InfinityFree, asi que esto
        // normalmente se resuelve al instante y este tiempo no llega a usarse.
        webView.postDelayed({ marcarResuelto() }, 3000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(
                    "document.documentElement.outerHTML.indexOf('aes.js') === -1"
                ) { resultado ->
                    if (resultado == "true") {
                        marcarResuelto()
                    }
                }
            }
        }
    }

    private fun marcarResuelto() {
        if (resuelto) return
        resuelto = true
        callbacksEsperando.forEach { it() }
        callbacksEsperando.clear()
    }
}
