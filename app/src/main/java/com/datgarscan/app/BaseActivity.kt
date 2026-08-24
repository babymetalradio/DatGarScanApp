package com.datgarscan.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Todas las Activities de la app heredan de esta, para que el bloqueo de
 * capturas/grabacion de pantalla (FLAG_SECURE) aplique en todos lados por
 * defecto, sin tener que repetirlo pantalla por pantalla.
 *
 * IMPORTANTE: debe ir ANTES de setContentView(). Si se agrega despues, el
 * primer frame puede llegar a dibujarse sin la proteccion activa.
 *
 * Si alguna pantalla puntual necesita permitir capturas (por ejemplo el
 * dialogo de aviso de actualizacion en MainActivity), puede llamar a
 * permitirCapturasTemporalmente() y restaurarProteccion() alrededor de
 * ese momento puntual.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Quita el bloqueo temporalmente (ej. mientras se muestra un dialogo que si se puede capturar). */
    protected fun permitirCapturasTemporalmente() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Vuelve a activar el bloqueo despues de permitirCapturasTemporalmente(). */
    protected fun restaurarProteccion() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
