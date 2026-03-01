package com.game.remoteclient.ui

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

            // Set avatar icon
            binding.avatarIcon.setImageResource(getAvatarDrawable(avatar.AvatarID))

            // Highlight selected card
            binding.avatarCard.strokeWidth = if (isSelected) 4 else 0
            binding.avatarCard.strokeColor = context.getColor(R.color.primary)

            binding.root.setOnClickListener {
                if (avatar.Available) {
                    onAvatarClick(avatar)
                }
            }
        }

        private fun getAvatarDrawable(avatarId: String): Int {
            return when (avatarId.uppercase()) {
                "COWGIRL" -> R.drawable.ic_avatar_cowgirl
                "GOFF" -> R.drawable.ic_avatar_goff
                "HOTDOGMAN" -> R.drawable.ic_avatar_hotdogman
                "LOVER" -> R.drawable.ic_avatar_lover
                "MOUNTAINEER" -> R.drawable.ic_avatar_mountaineer
                "SCIENTIST" -> R.drawable.ic_avatar_scientist
                "SPACEMAN" -> R.drawable.ic_avatar_spaceman
                "MAGICIAN" -> R.drawable.ic_avatar_magician
                else -> R.drawable.circle_background
            }
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
