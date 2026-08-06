package com.datgarscan.app.favoritos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.datgarscan.app.MangaAdapter
import com.datgarscan.app.databinding.ActivityFavoritosBinding
import com.datgarscan.app.detalle.SerieDetalleActivity
import com.datgarscan.app.webapi.MangaResumen
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch

class FavoritosActivity : AppCompatActivity() {

    companion object {
        fun crearIntent(context: Context): Intent = Intent(context, FavoritosActivity::class.java)
    }

    private lateinit var binding: ActivityFavoritosBinding
    private lateinit var adapter: MangaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitulo.text = "Mis favoritos"
        binding.tvVolver.setOnClickListener { finish() }

        adapter = MangaAdapter { manga ->
            startActivity(SerieDetalleActivity.crearIntent(this, manga.slug))
        }
        binding.rvLista.layoutManager = GridLayoutManager(this, 2)
        binding.rvLista.adapter = adapter

        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerAds)

        cargar()
    }

    private fun cargar() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvVacio.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().listarFavoritos()
                binding.progressBar.visibility = View.GONE

                if (!respuesta.success) {
                    binding.tvVacio.text = respuesta.message ?: "Error al cargar favoritos."
                    binding.tvVacio.visibility = View.VISIBLE
                    return@launch
                }

                if (respuesta.data.isEmpty()) {
                    binding.tvVacio.text = "Todavía no tienes favoritos.\nToca el boton de favorito en cualquier serie para agregarla."
                    binding.tvVacio.visibility = View.VISIBLE
                    return@launch
                }

                val mangas = respuesta.data.map {
                    MangaResumen(
                        id = it.id, slug = it.slug, title = it.title,
                        cover_url = it.cover_url, author = it.author,
                        status = null, chapter_count = it.chapter_count
                    )
                }
                adapter.actualizar(mangas)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvVacio.text = "Error de conexión: ${e.message}"
                binding.tvVacio.visibility = View.VISIBLE
            }
        }
    }
}
