package com.datgarscan.app.lector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.datgarscan.app.R

class TiraAdapter(
    private val urls: List<String>,
    private val onTap: (() -> Unit)? = null
) : RecyclerView.Adapter<TiraAdapter.TiraViewHolder>() {

    inner class TiraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iv: ImageView = itemView.findViewById(R.id.ivPaginaTira)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TiraViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pagina_tira, parent, false)
        return TiraViewHolder(vista)
    }

    override fun onBindViewHolder(holder: TiraViewHolder, position: Int) {
        holder.iv.setOnClickListener { onTap?.invoke() }
        Glide.with(holder.itemView.context)
            .load(urls[position])
            .into(holder.iv)
    }

    override fun getItemCount(): Int = urls.size
}
