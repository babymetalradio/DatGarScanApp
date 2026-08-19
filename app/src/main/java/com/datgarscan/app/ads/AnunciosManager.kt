package com.datgarscan.app.ads

import android.content.Context
import com.startapp.sdk.adsbase.StartAppAd

/**
 * Lleva la cuenta de cuantos capitulos se han abierto y muestra un anuncio
 * de pantalla completa cada cierto numero, en vez de en cada uno (que seria
 * demasiado invasivo para quien lee varios capitulos seguidos).
 */
object AnunciosManager {

    private const val PREFS = "datgar_ads"
    private const val KEY_CONTADOR = "capitulos_abiertos"
    private const val KEY_CONTADOR_SALIDA = "salidas_lector"
    private const val CADA_CUANTOS_CAPITULOS = 2
    private const val CADA_CUANTAS_SALIDAS = 2

    /**
     * Suma uno al contador y, si toca, muestra el intersticial.
     * Se llama al abrir un capitulo.
     */
    fun registrarCapituloAbierto(context: Context) {
        try {
        // Quien canjeo garritas por tiempo sin anuncios no ve intersticiales
        if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val contador = prefs.getInt(KEY_CONTADOR, 0) + 1

        if (contador >= CADA_CUANTOS_CAPITULOS) {
            prefs.edit().putInt(KEY_CONTADOR, 0).apply()
            mostrarIntersticial(context)
        } else {
            prefs.edit().putInt(KEY_CONTADOR, contador).apply()
        }
        } catch (e: Throwable) { /* si los anuncios fallan, la lectura sigue */ }
    }

    private fun mostrarIntersticial(context: Context) {
        try {
            StartAppAd.showAd(context)
        } catch (e: Exception) {
            // Si el anuncio no esta listo o falla, no interrumpimos la lectura
        }
    }

    /**
     * Igual que registrarCapituloAbierto, pero para cuando el usuario sale
     * del lector (boton volver o boton atras del sistema), con su propio
     * contador para no mostrar dos anuncios seguidos si justo coincide con
     * el de entrada.
     */
    fun registrarSalidaDeLector(context: Context) {
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val contador = prefs.getInt(KEY_CONTADOR_SALIDA, 0) + 1

            if (contador >= CADA_CUANTAS_SALIDAS) {
                prefs.edit().putInt(KEY_CONTADOR_SALIDA, 0).apply()
                mostrarIntersticial(context)
            } else {
                prefs.edit().putInt(KEY_CONTADOR_SALIDA, contador).apply()
            }
        } catch (e: Throwable) { /* si los anuncios fallan, la salida sigue */ }
    }

    /**
     * Oculta los banners que se le pasen, si el usuario canjeo tiempo sin
     * anuncios. Se llama desde cada pantalla que tenga banner.
     */
    fun ocultarBannersSiCorresponde(context: Context, vararg banners: android.view.View?) {
        try {
            if (!com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return
            banners.forEach { it?.visibility = android.view.View.GONE }
        } catch (e: Throwable) { /* no es critico */ }
    }
}
