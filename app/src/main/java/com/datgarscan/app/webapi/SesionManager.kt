package com.datgarscan.app.webapi

import android.content.Context

object SesionManager {

    private const val PREFS = "datgar_sesion"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"

    @Volatile var tokenEnMemoria: String? = null
        private set

    var usernameEnMemoria: String? = null
        private set

    var rolEnMemoria: String? = null
        private set

    fun cargar(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        tokenEnMemoria = prefs.getString(KEY_TOKEN, null)
        usernameEnMemoria = prefs.getString(KEY_USERNAME, null)
        rolEnMemoria = prefs.getString(KEY_ROLE, null)
    }

    fun guardarSesion(context: Context, token: String, username: String, role: String? = null) {
        tokenEnMemoria = token
        usernameEnMemoria = username
        rolEnMemoria = role
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun cerrarSesion(context: Context) {
        tokenEnMemoria = null
        usernameEnMemoria = null
        rolEnMemoria = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun estaLogueado(): Boolean = tokenEnMemoria != null

    fun esAdminOEditor(): Boolean = rolEnMemoria == "admin" || rolEnMemoria == "editor"
}
