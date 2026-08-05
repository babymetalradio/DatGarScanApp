package com.datgarscan.app.tienda

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Recuerda si el usuario canjeo garritas por tiempo sin anuncios. Se guarda
 * en el telefono para que el resto de la app pueda consultarlo al instante,
 * sin tener que preguntarle al servidor cada vez que va a mostrar un anuncio.
 */
object SinAnunciosManager {

    private const val PREFS = "datgar_sin_anuncios"
    private const val KEY_HASTA = "sin_anuncios_hasta_millis"

    fun guardar(context: Context, activo: Boolean, hastaTexto: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (!activo || hastaTexto == null) {
            prefs.edit().remove(KEY_HASTA).apply()
            return
        }

        try {
            val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val millis = formato.parse(hastaTexto)?.time ?: return
            prefs.edit().putLong(KEY_HASTA, millis).apply()
        } catch (e: Exception) { /* si no se puede leer la fecha, lo dejamos como estaba */ }
    }

    fun tieneSinAnuncios(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasta = prefs.getLong(KEY_HASTA, 0L)
        return hasta > System.currentTimeMillis()
    }

    fun limpiar(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
