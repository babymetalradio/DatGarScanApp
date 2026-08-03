package com.datgarscan.app.historial

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.datgarscan.app.databinding.ActivityFavoritosBinding
import com.datgarscan.app.lector.LectorActivity
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch

class HistorialActivity : AppCompatActivity() {

    companion object {
        fun crearIntent(context: Context): Intent = Intent(context, HistorialActivity::class.java)
    }

    private lateinit var binding: ActivityFavoritosBinding
    private lateinit var adapter: HistorialAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitulo.text = "Historial"
        binding.tvVolver.setOnClickListener { finish() }

        adapter = HistorialAdapter { item ->
            startActivity(LectorActivity.crearIntent(this, item.chapter_id))
        }
        binding.rvLista.layoutManager = LinearLayoutManager(this)
        binding.rvLista.adapter = adapter

        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerAds)

        cargar()
    }

    private fun cargar() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvVacio.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().listarHistorial()
                binding.progressBar.visibility = View.GONE

                if (!respuesta.success) {
                    binding.tvVacio.text = respuesta.message ?: "Error al cargar el historial."
                    binding.tvVacio.visibility = View.VISIBLE
                    return@launch
                }

                if (respuesta.data.isEmpty()) {
                    binding.tvVacio.text = "Todavía no has leído ningún capítulo."
                    binding.tvVacio.visibility = View.VISIBLE
                    return@launch
                }

                adapter.actualizar(respuesta.data)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvVacio.text = "Error de conexión: ${e.message}"
                binding.tvVacio.visibility = View.VISIBLE
            }
        }
    }
}
