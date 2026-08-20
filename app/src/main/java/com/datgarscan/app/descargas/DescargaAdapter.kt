package com.datgarscan.app.descargas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.datgarscan.app.R

class DescargaAdapter(
    private var items: List<CapituloDescargado> = emptyList(),
    private val onClick: (CapituloDescargado) -> Unit,
    private val onBorrar: (CapituloDescargado) -> Unit
) : RecyclerView.Adapter<DescargaAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iv: ImageView = view.findViewById(R.id.ivPortada)
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloManga)
        val tvCapitulo: TextView = view.findViewById(R.id.tvCapitulo)
        val tvBorrar: TextView = view.findViewById(R.id.tvBorrar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.item_descarga, parent, false)
        return ViewHolder(vista)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        Glide.with(holder.itemView.context).load(item.coverUrl).into(holder.iv)
        holder.tvTitulo.text = item.mangaTitle
        val numero = if (item.chapterNumber == item.chapterNumber.toLong().toDouble())
            item.chapterNumber.toLong().toString() else item.chapterNumber.toString()
        holder.tvCapitulo.text = "Cap $numero · ${item.totalPaginas} págs"
        holder.itemView.setOnClickListener { onClick(item) }
        holder.tvBorrar.setOnClickListener { onBorrar(item) }
    }

    override fun getItemCount(): Int = items.size

    fun actualizar(nuevaLista: List<CapituloDescargado>) {
        items = nuevaLista
        notifyDataSetChanged()
    }
}
