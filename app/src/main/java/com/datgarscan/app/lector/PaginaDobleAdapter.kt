package com.datgarscan.app.lector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.datgarscan.app.R

/**
 * Cada elemento es un par de URLs (izquierda, derecha). El segundo elemento
 * del par puede ser null si es la ultima pagina de un capitulo con numero
 * impar de paginas.
 */
class PaginaDobleAdapter(
    private val pares: List<Pair<String, String?>>,
    private val onTap: (() -> Unit)? = null
) : RecyclerView.Adapter<PaginaDobleAdapter.ParViewHolder>() {

    inner class ParViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIzq: ImageView = itemView.findViewById(R.id.ivPaginaIzq)
        val ivDer: ImageView = itemView.findViewById(R.id.ivPaginaDer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pagina_doble, parent, false)
        return ParViewHolder(vista)
    }

    override fun onBindViewHolder(holder: ParViewHolder, position: Int) {
        val (izq, der) = pares[position]
        holder.ivIzq.setOnClickListener { onTap?.invoke() }
        holder.ivDer.setOnClickListener { onTap?.invoke() }
        Glide.with(holder.itemView.context).load(izq).into(holder.ivIzq)
        if (der != null) {
            holder.ivDer.visibility = View.VISIBLE
            Glide.with(holder.itemView.context).load(der).into(holder.ivDer)
        } else {
            holder.ivDer.visibility = View.INVISIBLE
        }
    }

    override fun getItemCount(): Int = pares.size
}
