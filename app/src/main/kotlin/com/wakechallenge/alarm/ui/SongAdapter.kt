package com.wakechallenge.alarm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wakechallenge.alarm.data.MusicPoolEntity
import com.wakechallenge.alarm.databinding.ItemSongBinding

class SongAdapter(
    private val onPlayPause: (MusicPoolEntity) -> Unit,
    private val onDelete: (MusicPoolEntity) -> Unit
) : ListAdapter<MusicPoolEntity, SongAdapter.VH>(DIFF) {

    var currentlyPlayingId: Long? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class VH(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = getItem(position)
        holder.binding.textSongName.text = song.displayName
        holder.binding.buttonPlayPause.setImageResource(
            if (song.id == currentlyPlayingId) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        holder.binding.buttonPlayPause.setOnClickListener { onPlayPause(song) }
        holder.binding.buttonDelete.setOnClickListener { onDelete(song) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<MusicPoolEntity>() {
            override fun areItemsTheSame(old: MusicPoolEntity, new: MusicPoolEntity) = old.id == new.id
            override fun areContentsTheSame(old: MusicPoolEntity, new: MusicPoolEntity) = old == new
        }
    }
}
