package com.datgarscan.app.detalle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.datgarscan.app.R
import com.datgarscan.app.databinding.ActivitySerieDetalleBinding
import com.datgarscan.app.descargas.DescargasManager
import com.datgarscan.app.descargas.EstadoDescargaCap
import com.datgarscan.app.lector.LectorActivity
import com.datgarscan.app.webapi.CapituloResumen
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch

class SerieDetalleActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SLUG = "extra_slug"

        fun crearIntent(context: Context, slug: String): Intent {
            return Intent(context, SerieDetalleActivity::class.java)
                .putExtra(EXTRA_SLUG, slug)
        }
    }

    private lateinit var binding: ActivitySerieDetalleBinding
    private lateinit var adapter: CapituloAdapter
    private var mangaIdActual: Int? = null
    private var esFavoritoActual: Boolean = false
    private var slugActual: String = ""
    private var mangaTitleActual: String = ""
    private var coverUrlActual: String? = null
    private var capitulosActuales: List<CapituloResumen> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySerieDetalleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val slug = intent.getStringExtra(EXTRA_SLUG)
        if (slug == null) {
            Toast.makeText(this, "Serie inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        slugActual = slug

        binding.tvVolver.setOnClickListener { finish() }
        binding.tvFavorito.setOnClickListener { alternarFavorito() }
        binding.tvMasInfo.setOnClickListener {
            startActivity(MangaInfoActivity.crearIntent(this, slugActual))
        }

        adapter = CapituloAdapter(
            onClick = { capitulo -> startActivity(LectorActivity.crearIntent(this, capitulo.id)) },
            onDescargar = { capitulo -> descargarCapitulo(capitulo) },
            onBorrarDescarga = { capitulo -> borrarDescarga(capitulo) }
        )
        binding.rvCapitulos.layoutManager = LinearLayoutManager(this)
        binding.rvCapitulos.adapter = adapter

        binding.tvLeerPrimero.setOnClickListener {
            capitulosActuales.minByOrNull { it.chapter_number }?.let {
                startActivity(LectorActivity.crearIntent(this, it.id))
            }
        }
        binding.tvLeerUltimo.setOnClickListener {
            capitulosActuales.maxByOrNull { it.chapter_number }?.let {
                startActivity(LectorActivity.crearIntent(this, it.id))
            }
        }

        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerAds)

        cargarDetalle(slug)
    }

    private fun alternarFavorito() {
        val mangaId = mangaIdActual ?: return

        if (!com.datgarscan.app.webapi.SesionManager.estaLogueado()) {
            Toast.makeText(this, "Inicia sesión para guardar favoritos.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().alternarFavorito(
                    com.datgarscan.app.webapi.FavoritoToggleRequest(mangaId)
                )
                if (respuesta.success) {
                    esFavoritoActual = respuesta.es_favorito
                    actualizarBotonFavorito()
                } else {
                    Toast.makeText(this@SerieDetalleActivity, respuesta.message ?: "No se pudo actualizar.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SerieDetalleActivity, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun actualizarBotonFavorito() {
        binding.tvFavorito.text = if (esFavoritoActual) "En favoritos" else "Favorito"
        binding.tvFavorito.setTextColor(
            resources.getColor(if (esFavoritoActual) R.color.accent else R.color.muted, theme)
        )
    }

    private fun cargarDetalle(slug: String) {
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerDetalle(slug)

                if (!respuesta.success || respuesta.data == null) {
                    Toast.makeText(this@SerieDetalleActivity, respuesta.message ?: "No se encontró la serie.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                val manga = respuesta.data
                mangaIdActual = manga.id
                esFavoritoActual = manga.es_favorito
                actualizarBotonFavorito()

                binding.tvNombreSerie.text = manga.title
                binding.tvAutorDetalle.text = manga.author?.takeIf { it.isNotBlank() } ?: "Autor desconocido"
                binding.tvContador.text = "${manga.chapters.size} capítulos"

                if (manga.genres.isNotEmpty()) {
                    binding.tvGenerosDetalle.visibility = View.VISIBLE
                    binding.tvGenerosDetalle.text = manga.genres.joinToString(" · ")
                }

                val descripcion = manga.description?.takeIf { it.isNotBlank() }
                if (descripcion != null) {
                    binding.tvDescripcionDetalle.visibility = View.VISIBLE
                    binding.tvDescripcionDetalle.text = descripcion

                    // Tocar la descripcion la despliega completa y la vuelve a recortar
                    binding.tvDescripcionDetalle.setOnClickListener {
                        val estaRecortada = binding.tvDescripcionDetalle.maxLines == 4
                        binding.tvDescripcionDetalle.maxLines = if (estaRecortada) Int.MAX_VALUE else 4
                    }
                }

                Glide.with(this@SerieDetalleActivity).load(manga.cover_url).into(binding.ivPortadaDetalle)

                mangaTitleActual = manga.title
                coverUrlActual = manga.cover_url
                capitulosActuales = manga.chapters
                adapter.actualizar(manga.chapters)

                val idsDescargados = manga.chapters
                    .filter { DescargasManager.estaDescargado(this@SerieDetalleActivity, it.id) }
                    .map { it.id }
                    .toSet()
                if (idsDescargados.isNotEmpty()) {
                    adapter.marcarDescargados(idsDescargados)
                }

            } catch (e: Exception) {
                Log.e("SerieDetalleActivity", "Error cargando detalle", e)
                Toast.makeText(this@SerieDetalleActivity, com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun descargarCapitulo(capitulo: CapituloResumen) {
        val mangaId = mangaIdActual ?: return
        adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.Descargando(0, capitulo.pages))

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerCapitulo(capitulo.id)
                val paginasCapitulo = respuesta.data?.pages ?: emptyList()

                if (!respuesta.success || paginasCapitulo.isEmpty()) {
                    Toast.makeText(this@SerieDetalleActivity, "No se pudo descargar el capítulo.", Toast.LENGTH_SHORT).show()
                    adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.NoDescargado)
                    return@launch
                }

                val exito = DescargasManager.descargarCapitulo(
                    context = this@SerieDetalleActivity,
                    chapterId = capitulo.id,
                    mangaId = mangaId,
                    mangaSlug = slugActual,
                    mangaTitle = mangaTitleActual,
                    chapterNumber = capitulo.chapter_number,
                    chapterTitle = capitulo.title,
                    coverUrl = coverUrlActual,
                    paginasUrls = paginasCapitulo,
                    onProgreso = { descargadas, total ->
                        runOnUiThread {
                            adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.Descargando(descargadas, total))
                        }
                    }
                )

                adapter.actualizarEstadoDescarga(
                    capitulo.id,
                    if (exito) EstadoDescargaCap.Descargado else EstadoDescargaCap.NoDescargado
                )
                if (!exito) {
                    Toast.makeText(this@SerieDetalleActivity, "Falló la descarga, intenta de nuevo.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SerieDetalleActivity, "Capítulo descargado.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SerieDetalleActivity", "Error descargando capitulo", e)
                adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.NoDescargado)
                Toast.makeText(this@SerieDetalleActivity, "Error de conexión al descargar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun borrarDescarga(capitulo: CapituloResumen) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Borrar descarga")
            .setMessage("¿Borrar la descarga del Cap ${capitulo.chapter_number}?")
            .setPositiveButton("Borrar") { _, _ ->
                DescargasManager.borrarDescarga(this, capitulo.id)
                adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.NoDescargado)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
