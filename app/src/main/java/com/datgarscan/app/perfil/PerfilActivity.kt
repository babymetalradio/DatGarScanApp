package com.datgarscan.app.perfil

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.datgarscan.app.databinding.ActivityPerfilBinding
import com.datgarscan.app.webapi.ErroresRed
import com.datgarscan.app.webapi.PerfilData
import com.datgarscan.app.webapi.SesionManager
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class PerfilActivity : AppCompatActivity() {

    companion object {
        fun crearIntent(context: Context): Intent = Intent(context, PerfilActivity::class.java)
    }

    private lateinit var binding: ActivityPerfilBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!SesionManager.estaLogueado()) {
            Toast.makeText(this, "Inicia sesión para ver tu perfil.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvVolver.setOnClickListener { finish() }
        binding.btnTienda.setOnClickListener {
            startActivity(com.datgarscan.app.tienda.TiendaActivity.crearIntent(this))
        }
        binding.btnFavoritos.setOnClickListener {
            startActivity(com.datgarscan.app.favoritos.FavoritosActivity.crearIntent(this))
        }
        binding.btnHistorial.setOnClickListener {
            startActivity(com.datgarscan.app.historial.HistorialActivity.crearIntent(this))
        }
        binding.btnCerrarSesion.setOnClickListener { confirmarCerrarSesion() }

        // Bottom nav
        binding.navInicio.setOnClickListener {
            startActivity(android.content.Intent(this, com.datgarscan.app.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
        binding.navExplorar.setOnClickListener {
            startActivity(android.content.Intent(this, com.datgarscan.app.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
        binding.navFavoritos.setOnClickListener {
            startActivity(com.datgarscan.app.favoritos.FavoritosActivity.crearIntent(this))
        }
        binding.navSeries.setOnClickListener {
            startActivity(com.datgarscan.app.historial.HistorialActivity.crearIntent(this))
        }
        binding.navPerfil.setOnClickListener {
            // Ya estamos aquí
        }

        // Muestra de inmediato lo que ya sabemos, sin esperar al servidor
        binding.tvUsername.text = SesionManager.usernameEnMemoria ?: ""
        binding.tvInicial.text = (SesionManager.usernameEnMemoria ?: "?").take(1).uppercase()
    }

    override fun onResume() {
        super.onResume()
        cargarPerfil()
        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerPerfil)
    }

    private fun cargarPerfil() {
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerPerfil()
                if (respuesta.success && respuesta.data != null) {
                    pintarPerfil(respuesta.data)
                }
            } catch (e: Exception) {
                Toast.makeText(this@PerfilActivity, ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pintarPerfil(perfil: PerfilData) {
        binding.tvUsername.text = perfil.username
        binding.tvInicial.text = perfil.username.take(1).uppercase()

        binding.tvNumFavoritos.text = perfil.favoritos.toString()
        binding.tvNumCapitulos.text = perfil.capitulos_leidos.toString()
        binding.tvNumSeries.text = perfil.series_leidas.toString()

        binding.tvTextoTienda.text = "Tienda Garritas · ${perfil.garritas}"

        binding.tvEtiquetaSinAnuncios.visibility =
            if (perfil.sin_anuncios) View.VISIBLE else View.GONE

        binding.tvMiembroDesde.text = perfil.miembro_desde?.let { fecha ->
            try {
                val entrada = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val salida = SimpleDateFormat("MMMM 'de' yyyy", Locale("es"))
                "Miembro desde ${salida.format(entrada.parse(fecha) ?: return@let "")}"
            } catch (e: Exception) {
                ""
            }
        } ?: ""
    }

    private fun confirmarCerrarSesion() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                SesionManager.cerrarSesion(this)
                com.datgarscan.app.tienda.SinAnunciosManager.limpiar(this)
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
