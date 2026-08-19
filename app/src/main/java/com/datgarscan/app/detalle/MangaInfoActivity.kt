package com.datgarscan.app.detalle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.datgarscan.app.databinding.ActivityMangaInfoBinding
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch

class MangaInfoActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SLUG = "extra_slug"

        fun crearIntent(context: Context, slug: String): Intent {
            return Intent(context, MangaInfoActivity::class.java)
                .putExtra(EXTRA_SLUG, slug)
        }
    }

    private lateinit var binding: ActivityMangaInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMangaInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val slug = intent.getStringExtra(EXTRA_SLUG)
        if (slug == null) {
            Toast.makeText(this, "Serie invalida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvVolver.setOnClickListener { finish() }
        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerAds)

        cargarInfo(slug)
    }

    private fun cargarInfo(slug: String) {
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerDetalle(slug)

                if (!respuesta.success || respuesta.data == null) {
                    Toast.makeText(this@MangaInfoActivity, respuesta.message ?: "No se pudo cargar la informacion.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                val manga = respuesta.data

                Glide.with(this@MangaInfoActivity).load(manga.cover_url).into(binding.ivPortada)
                binding.tvTitulo.text = manga.title
                binding.tvGeneros.text = manga.genres.joinToString(", ")
                binding.tvAutor.text = manga.author?.takeIf { it.isNotBlank() } ?: "Desconocido"
                binding.tvEstado.text = traducirEstado(manga.status)
                binding.tvCantidadCapitulos.text = manga.chapters.size.toString()
                binding.tvDescripcion.text = manga.description?.takeIf { it.isNotBlank() }
                    ?: "Sin descripcion todavia."

            } catch (e: Exception) {
                Toast.makeText(this@MangaInfoActivity, "Error de conexion: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun traducirEstado(status: String?): String {
        return when (status) {
            "ongoing" -> "En curso"
            "completed" -> "Completado"
            "hiatus" -> "En pausa"
            "cancelled" -> "Cancelado"
            else -> "Desconocido"
        }
    }
}
