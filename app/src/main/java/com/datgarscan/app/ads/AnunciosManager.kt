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
    private const val CADA_CUANTOS_CAPITULOS = 3

    /**
     * Suma uno al contador y, si toca, muestra el intersticial.
     * Se llama al abrir un capitulo.
     */
    fun registrarCapituloAbierto(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val contador = prefs.getInt(KEY_CONTADOR, 0) + 1

        if (contador >= CADA_CUANTOS_CAPITULOS) {
            prefs.edit().putInt(KEY_CONTADOR, 0).apply()
            mostrarIntersticial(context)
        } else {
            prefs.edit().putInt(KEY_CONTADOR, contador).apply()
        }
    }

    private fun mostrarIntersticial(context: Context) {
        try {
            StartAppAd.showAd(context)
        } catch (e: Exception) {
            // Si el anuncio no esta listo o falla, no interrumpimos la lectura
        }
    }
}
