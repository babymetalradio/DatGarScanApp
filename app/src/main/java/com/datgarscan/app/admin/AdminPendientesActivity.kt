package com.datgarscan.app.admin

import com.datgarscan.app.BaseActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.datgarscan.app.databinding.ActivityAdminPendientesBinding
import com.datgarscan.app.webapi.MangaPendiente
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch

/**
 * Panel admin: revisa todos los mangas mapeados contra el blog y muestra
 * los capitulos que todavia no estan importados. Solo accesible para
 * usuarios con rol admin/editor (ver SesionManager.esAdminOEditor()).
 */
class AdminPendientesActivity : BaseActivity() {

    companion object {
        fun crearIntent(context: Context): Intent = Intent(context, AdminPendientesActivity::class.java)
    }

    private lateinit var binding: ActivityAdminPendientesBinding
    private lateinit var adapter: PendientesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPendientesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitulo.text = "Capítulos pendientes"
        binding.tvVolver.setOnClickListener { finish() }
        binding.tvEscanear.setOnClickListener { escanear() }

        adapter = PendientesAdapter { manga, capitulo -> importar(manga, capitulo) }
        binding.rvLista.layoutManager = LinearLayoutManager(this)
        binding.rvLista.adapter = adapter

        escanear()
    }

    private fun escanear() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvVacio.visibility = View.GONE
        binding.tvEscanear.isEnabled = false

        val acumulado = mutableListOf<MangaPendiente>()

        lifecycleScope.launch {
            try {
                var pagina = 0
                while (true) {
                    val respuesta = WebApiClient.getAdmin().listarPendientes(pagina = pagina)

                    if (!respuesta.success) {
                        binding.progressBar.visibility = View.GONE
                        binding.tvEscanear.isEnabled = true
                        Toast.makeText(this@AdminPendientesActivity, respuesta.message ?: "Error al escanear.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    acumulado.addAll(respuesta.pendientes)
                    adapter.actualizar(acumulado.toList())

                    binding.tvTitulo.text = if (respuesta.siguiente_pagina != null)
                        "Escaneando... (${respuesta.escaneados_hasta_ahora}/${respuesta.total_mangas})"
                    else
                        "Capítulos pendientes"

                    if (respuesta.siguiente_pagina == null) break
                    pagina = respuesta.siguiente_pagina
                }

                binding.progressBar.visibility = View.GONE
                binding.tvEscanear.isEnabled = true

                if (acumulado.isEmpty()) {
                    binding.tvVacio.text = "Todo al día — no hay capítulos pendientes de importar."
                    binding.tvVacio.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvEscanear.isEnabled = true
                binding.tvTitulo.text = "Capítulos pendientes"
                Toast.makeText(this@AdminPendientesActivity, com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importar(manga: MangaPendiente, capitulo: com.datgarscan.app.webapi.CapituloPendiente) {
        adapter.actualizarEstado(manga.manga_id, capitulo.numero, EstadoImportacion.Importando)

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.getAdmin().importarCapituloPendiente(
                    mangaId = manga.manga_id,
                    numero = capitulo.numero
                )
                if (respuesta.success) {
                    adapter.actualizarEstado(manga.manga_id, capitulo.numero, EstadoImportacion.Importado)
                } else {
                    adapter.actualizarEstado(manga.manga_id, capitulo.numero, EstadoImportacion.Inactivo)
                    Toast.makeText(this@AdminPendientesActivity, respuesta.message ?: "No se pudo importar.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                adapter.actualizarEstado(manga.manga_id, capitulo.numero, EstadoImportacion.Inactivo)
                Toast.makeText(this@AdminPendientesActivity, com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
