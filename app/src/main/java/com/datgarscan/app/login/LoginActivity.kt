package com.datgarscan.app.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.datgarscan.app.databinding.ActivityLoginBinding
import com.datgarscan.app.webapi.LoginRequest
import com.datgarscan.app.webapi.RegistroRequest
import com.datgarscan.app.webapi.SesionManager
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        fun crearIntent(context: Context): Intent = Intent(context, LoginActivity::class.java)
    }

    private lateinit var binding: ActivityLoginBinding
    private var modoRegistro = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvVolver.setOnClickListener { finish() }
        binding.tvCambiarModo.setOnClickListener {
            modoRegistro = !modoRegistro
            actualizarModo()
        }
        binding.btnEnviar.setOnClickListener {
            if (modoRegistro) registrar() else iniciarSesion()
        }

        actualizarModo()
    }

    private fun actualizarModo() {
        binding.tvError.visibility = View.GONE
        if (modoRegistro) {
            binding.tvTitulo.text = "Crear cuenta"
            binding.etEmail.visibility = View.VISIBLE
            binding.btnEnviar.text = "Registrarme"
            binding.tvCambiarModo.text = "¿Ya tienes cuenta? Inicia sesión"
        } else {
            binding.tvTitulo.text = "Iniciar sesión"
            binding.etEmail.visibility = View.GONE
            binding.btnEnviar.text = "Entrar"
            binding.tvCambiarModo.text = "¿No tienes cuenta? Regístrate"
        }
    }

    private fun iniciarSesion() {
        val usuario = binding.etUsuario.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (usuario.isEmpty() || password.isEmpty()) {
            mostrarError("Completa usuario y contraseña.")
            return
        }

        ponerCargando(true)
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().login(LoginRequest(usuario, password))
                ponerCargando(false)

                if (!respuesta.success || respuesta.token == null || respuesta.user == null) {
                    mostrarError(respuesta.message ?: "No se pudo iniciar sesión.")
                    return@launch
                }

                SesionManager.guardarSesion(this@LoginActivity, respuesta.token, respuesta.user.username)
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                ponerCargando(false)
                mostrarError("Error de conexión: ${e.message}")
            }
        }
    }

    private fun registrar() {
        val email = binding.etEmail.text.toString().trim()
        val usuario = binding.etUsuario.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (email.isEmpty() || usuario.isEmpty() || password.isEmpty()) {
            mostrarError("Completa todos los campos.")
            return
        }

        ponerCargando(true)
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().registro(RegistroRequest(usuario, email, password))
                ponerCargando(false)

                if (!respuesta.success || respuesta.token == null || respuesta.user == null) {
                    mostrarError(respuesta.message ?: "No se pudo registrar la cuenta.")
                    return@launch
                }

                SesionManager.guardarSesion(this@LoginActivity, respuesta.token, respuesta.user.username)
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                ponerCargando(false)
                mostrarError("Error de conexión: ${e.message}")
            }
        }
    }

    private fun ponerCargando(cargando: Boolean) {
        binding.progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnEnviar.isEnabled = !cargando
        binding.tvError.visibility = View.GONE
    }

    private fun mostrarError(mensaje: String) {
        binding.tvError.text = mensaje
        binding.tvError.visibility = View.VISIBLE
    }
}
