package com.datgarscan.app.webapi

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErroresRed {

    fun esErrorDeConexion(e: Throwable): Boolean {
        return e is UnknownHostException || e is ConnectException || e is SocketTimeoutException
    }

    fun mensajeAmable(e: Throwable): String {
        return if (esErrorDeConexion(e)) {
            "Sin conexion a internet. Revisa tu conexion e intenta de nuevo."
        } else {
            "Error de conexion: ${e.javaClass.simpleName} - ${e.message}"
        }
    }
}
