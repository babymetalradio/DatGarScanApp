package com.datgarscan.app.tienda

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Recuerda si el usuario tiene Pro activo (sin anuncios + descargas de
 * capitulos), igual que SinAnunciosManager. Se guarda en el telefono para
 * que el resto de la app (ej. el boton de descargar) pueda consultarlo al
 * instante sin llamar al servidor cada vez.
 */
object ProManager {

    private const val PREFS = "datgar_pro"
    private const val KEY_HASTA = "pro_hasta_millis"

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

    fun esPro(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasta = prefs.getLong(KEY_HASTA, 0L)
        return hasta > System.currentTimeMillis()
    }

    fun limpiar(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
