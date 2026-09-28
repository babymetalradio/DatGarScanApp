package com.datgarscan.app.webapi

import android.content.Context

object SesionManager {

    private const val PREFS = "datgar_sesion"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"

    /** Application context para poder limpiar prefs desde el interceptor (401). */
    @Volatile private var appContext: Context? = null

    @Volatile var tokenEnMemoria: String? = null
        private set

    var usernameEnMemoria: String? = null
        private set

    var rolEnMemoria: String? = null
        private set

    fun cargar(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)?.trim()
        // Token vacío no cuenta como sesión
        if (token.isNullOrEmpty()) {
            tokenEnMemoria = null
            usernameEnMemoria = null
            rolEnMemoria = null
            prefs.edit().clear().apply()
            return
        }
        tokenEnMemoria = token
        usernameEnMemoria = prefs.getString(KEY_USERNAME, null)
        rolEnMemoria = prefs.getString(KEY_ROLE, null)
    }

    fun guardarSesion(context: Context, token: String, username: String, role: String? = null) {
        appContext = context.applicationContext
        val t = token.trim()
        if (t.isEmpty()) {
            cerrarSesion(context)
            return
        }
        tokenEnMemoria = t
        usernameEnMemoria = username
        rolEnMemoria = role
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, t)
            .putString(KEY_USERNAME, username)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun cerrarSesion(context: Context) {
        appContext = context.applicationContext
        tokenEnMemoria = null
        usernameEnMemoria = null
        rolEnMemoria = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        // También limpia el beneficio de sin anuncios local al cerrar sesión
        try {
            com.datgarscan.app.tienda.SinAnunciosManager.limpiar(context)
        } catch (_: Throwable) { }
    }

    /**
     * Limpia la sesión cuando el servidor responde 401 (token vencido o inválido).
     * Puede llamarse sin Activity (interceptor de red).
     */
    fun cerrarSesionPor401() {
        tokenEnMemoria = null
        usernameEnMemoria = null
        rolEnMemoria = null
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
        appContext?.let { ctx ->
            try {
                com.datgarscan.app.tienda.SinAnunciosManager.limpiar(ctx)
            } catch (_: Throwable) { }
        }
    }

    /** Hay token local. No garantiza que el servidor lo acepte (puede estar vencido). */
    fun estaLogueado(): Boolean = !tokenEnMemoria.isNullOrBlank()

    fun esAdminOEditor(): Boolean = rolEnMemoria == "admin" || rolEnMemoria == "editor"
}
