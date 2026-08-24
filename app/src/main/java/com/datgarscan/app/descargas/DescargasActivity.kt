package com.datgarscan.app.descargas

import com.datgarscan.app.BaseActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.datgarscan.app.databinding.ActivityDescargasBinding
import com.datgarscan.app.lector.LectorActivity

class DescargasActivity : BaseActivity() {

    companion object {
        fun crearIntent(context: Context): Intent = Intent(context, DescargasActivity::class.java)
    }

    private lateinit var binding: ActivityDescargasBinding
    private lateinit var adapter: DescargaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDescargasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitulo.text = "Mis descargas"
        binding.tvVolver.setOnClickListener { finish() }
        binding.progressBar.visibility = View.GONE

        adapter = DescargaAdapter(
            onClick = { item -> startActivity(LectorActivity.crearIntent(this, item.chapterId)) },
            onBorrar = { item ->
                android.app.AlertDialog.Builder(this)
                    .setTitle("Borrar descarga")
                    .setMessage("¿Borrar \"${item.mangaTitle}\" Cap ${item.chapterNumber}? Esto libera el espacio que ocupa en el dispositivo.")
                    .setPositiveButton("Borrar") { _, _ ->
                        DescargasManager.borrarDescarga(this, item.chapterId)
                        cargar()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )
        binding.rvLista.layoutManager = LinearLayoutManager(this)
        binding.rvLista.adapter = adapter

        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerAds)
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        val lista = DescargasManager.listarDescargas(this)
        if (lista.isEmpty()) {
            binding.tvVacio.text = "Todavía no descargaste ningún capítulo.\nDescárgalos desde la página de cada serie para leerlos sin conexión."
            binding.tvVacio.visibility = View.VISIBLE
        } else {
            binding.tvVacio.visibility = View.GONE
        }
        adapter.actualizar(lista)
    }
}
