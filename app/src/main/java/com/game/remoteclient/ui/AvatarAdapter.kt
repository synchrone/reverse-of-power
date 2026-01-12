package com.game.remoteclient.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.game.protocol.ServerAvatarStatusMessage
import com.game.remoteclient.R
import com.game.remoteclient.databinding.ItemAvatarBinding

class AvatarAdapter(
    private val onAvatarClick: (ServerAvatarStatusMessage) -> Unit
) : ListAdapter<ServerAvatarStatusMessage, AvatarAdapter.AvatarViewHolder>(AvatarDiffCallback()) {

    private var selectedAvatarId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val binding = ItemAvatarBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AvatarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setSelectedAvatar(avatarId: String?) {
        val oldSelected = selectedAvatarId
        selectedAvatarId = avatarId
        // Refresh items that changed selection state
        currentList.forEachIndexed { index, avatar ->
            if (avatar.AvatarID == oldSelected || avatar.AvatarID == avatarId) {
                notifyItemChanged(index)
            }
        }
    }

    inner class AvatarViewHolder(
        private val binding: ItemAvatarBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(avatar: ServerAvatarStatusMessage) {
            binding.avatarName.text = avatar.AvatarID

            val isSelected = avatar.AvatarID == selectedAvatarId
            val context = binding.root.context

            // Set availability status
            if (avatar.Available) {
                binding.avatarStatus.text = if (isSelected) "Selected" else "Available"
                binding.avatarStatus.setTextColor(context.getColor(R.color.primary))
                binding.root.alpha = 1.0f
                binding.root.isEnabled = true
            } else {
                binding.avatarStatus.text = "Taken"
                binding.avatarStatus.setTextColor(context.getColor(R.color.text_secondary))
                binding.root.alpha = 0.5f
                binding.root.isEnabled = false
            }

            // Set color indicator based on avatar ID hash
            val color = getAvatarColor(avatar.AvatarID)
            val drawable = binding.avatarColorIndicator.background as? GradientDrawable
                ?: GradientDrawable().also { binding.avatarColorIndicator.background = it }
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(color)

            // Highlight selected card
            binding.avatarCard.strokeWidth = if (isSelected) 4 else 0
            binding.avatarCard.strokeColor = context.getColor(R.color.primary)

            binding.root.setOnClickListener {
                if (avatar.Available) {
                    onAvatarClick(avatar)
                }
            }
        }

        private fun getAvatarColor(avatarId: String): Int {
            val colors = listOf(
                Color.parseColor("#F44336"), // Red
                Color.parseColor("#E91E63"), // Pink
                Color.parseColor("#9C27B0"), // Purple
                Color.parseColor("#673AB7"), // Deep Purple
                Color.parseColor("#3F51B5"), // Indigo
                Color.parseColor("#2196F3"), // Blue
                Color.parseColor("#00BCD4"), // Cyan
                Color.parseColor("#009688"), // Teal
                Color.parseColor("#4CAF50"), // Green
                Color.parseColor("#FF9800"), // Orange
            )
            return colors[Math.abs(avatarId.hashCode()) % colors.size]
        }
    }

    private class AvatarDiffCallback : DiffUtil.ItemCallback<ServerAvatarStatusMessage>() {
        override fun areItemsTheSame(
            oldItem: ServerAvatarStatusMessage,
            newItem: ServerAvatarStatusMessage
        ): Boolean {
            return oldItem.AvatarID == newItem.AvatarID
        }

        override fun areContentsTheSame(
            oldItem: ServerAvatarStatusMessage,
            newItem: ServerAvatarStatusMessage
        ): Boolean {
            return oldItem == newItem
        }
    }
}
