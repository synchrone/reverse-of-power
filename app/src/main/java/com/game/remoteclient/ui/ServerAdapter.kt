package com.game.remoteclient.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.game.remoteclient.databinding.ItemServerBinding
import com.game.remoteclient.models.GameServer

class ServerAdapter(
    private val onServerClick: (GameServer) -> Unit
) : ListAdapter<GameServer, ServerAdapter.ServerViewHolder>(ServerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ServerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServerViewHolder(
        private val binding: ItemServerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(server: GameServer) {
            binding.serverName.text = server.name ?: "PlayStation"
            binding.serverAddress.text = server.ipAddress
            binding.consoleType.text = server.consoleType ?: "PS"

            binding.root.setOnClickListener {
                onServerClick(server)
            }
        }
    }

    private class ServerDiffCallback : DiffUtil.ItemCallback<GameServer>() {
        override fun areItemsTheSame(oldItem: GameServer, newItem: GameServer): Boolean {
            return oldItem.ipAddress == newItem.ipAddress
        }

        override fun areContentsTheSame(oldItem: GameServer, newItem: GameServer): Boolean {
            return oldItem == newItem
        }
    }
}
