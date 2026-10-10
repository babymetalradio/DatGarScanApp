package com.datgarscan.app.webapi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Conectividad {

    /**
     * true si el telefono tiene una red con salida a internet (WiFi o datos).
     * Si no se puede saber, asume que si hay, para no bloquear la app por error.
     */
    fun hayInternet(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val red = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(red) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Throwable) {
            true
        }
    }
}
