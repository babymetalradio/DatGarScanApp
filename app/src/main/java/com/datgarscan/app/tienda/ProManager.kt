package com.datgarscan.app.tienda

import android.content.Context

/**
 * Pro = sin anuncios activo. No tiene almacenamiento propio a proposito:
 * pregunta directo a SinAnunciosManager, que es la unica fuente de verdad
 * tanto en el servidor (columna sin_anuncios_hasta) como en el telefono.
 * Asi es imposible que "Pro" y "sin anuncios" queden desincronizados.
 */
object ProManager {
    fun esPro(context: Context): Boolean = SinAnunciosManager.tieneSinAnuncios(context)
}
