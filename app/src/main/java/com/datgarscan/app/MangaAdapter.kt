package com.datgarscan.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.datgarscan.app.webapi.MangaResumen
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaAdapter(
    private var mangas: List<MangaResumen> = emptyList(),
    private val onClick: (MangaResumen) -> Unit
) : RecyclerView.Adapter<MangaAdapter.MangaViewHolder>() {

    inner class MangaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPortada: ImageView = view.findViewById(R.id.ivPortada)
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val tvNuevo: TextView = view.findViewById(R.id.tvNuevo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manga, parent, false)
        return MangaViewHolder(vista)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        val manga = mangas[position]
        holder.tvNombre.text = manga.title
        holder.tvMeta.text = "${manga.chapter_count} caps."
        holder.tvNuevo.visibility = if (esReciente(manga.last_chapter_at)) View.VISIBLE else View.GONE

        Glide.with(holder.itemView.context)
            .load(manga.cover_url)
            .centerCrop()
            .placeholder(R.color.surface2)
            .error(R.color.surface2)
            .into(holder.ivPortada)

        holder.itemView.setOnClickListener { onClick(manga) }
    }

    override fun getItemCount(): Int = mangas.size

    fun actualizar(nuevaLista: List<MangaResumen>) {
        mangas = nuevaLista
        notifyDataSetChanged()
    }

    /** Considera "nuevo" un capítulo publicado en los últimos 3 días. */
    private fun esReciente(fecha: String?): Boolean {
        if (fecha.isNullOrBlank()) return false
        return try {
            val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val instante = formato.parse(fecha) ?: return false
            val diffMillis = System.currentTimeMillis() - instante.time
            diffMillis in 0..TimeUnit.DAYS.toMillis(3)
        } catch (e: Exception) {
            false
        }
    }
}
