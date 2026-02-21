package com.game.remoteclient.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.game.protocol.ServerAvatarStatusMessage
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.databinding.FragmentAvatarSelectionBinding
import com.game.remoteclient.R
import java.io.ByteArrayOutputStream
import java.io.File

class AvatarSelectionFragment : Fragment() {

    private var _binding: FragmentAvatarSelectionBinding? = null
    private val binding get() = _binding!!

    private val args: AvatarSelectionFragmentArgs by navArgs()
    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var capturedBitmap: Bitmap? = null
    private lateinit var avatarAdapter: AvatarAdapter
    private var selectedAvatar: ServerAvatarStatusMessage? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAvatarSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAvatarList()
        setupListeners()
        observeAvatars()
        loadCapturedPhoto()
    }

    private fun loadCapturedPhoto() {
        val file = File(requireContext().cacheDir, CameraCaptureFragment.PHOTO_FILENAME)
        if (file.exists()) {
            capturedBitmap = BitmapFactory.decodeFile(file.absolutePath)
        }
    }

    private fun setupAvatarList() {
        avatarAdapter = AvatarAdapter { avatar ->
            onAvatarSelected(avatar)
        }

        binding.avatarRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = avatarAdapter
        }
    }

    private fun observeAvatars() {
        binding.loadingProgress.visibility = View.VISIBLE
        binding.emptyText.visibility = View.GONE

        networkManager.onAvatarsChanged = {
            activity?.runOnUiThread {
                updateAvatarList()
            }
        }

        networkManager.onAvatarRequestResponse = { response ->
            activity?.runOnUiThread {
                if (response.AvatarID == selectedAvatar?.AvatarID) {
                    updateContinueButton()
                    if (!response.Available) {
                        Toast.makeText(requireContext(), "Avatar unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Initial load
        updateAvatarList()
    }

    private fun updateAvatarList() {
        val avatars = networkManager.availableAvatars

        binding.loadingProgress.visibility = View.GONE

        if (avatars.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.avatarRecyclerView.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.avatarRecyclerView.visibility = View.VISIBLE
            avatarAdapter.submitList(avatars.toList())
        }

        updateContinueButton()
    }

    private fun onAvatarSelected(avatar: ServerAvatarStatusMessage) {
        selectedAvatar = avatar
        avatarAdapter.setSelectedAvatar(avatar.AvatarID)
        networkManager.requestAvatar(avatar.AvatarID)
        updateContinueButton()
    }

    private fun updateContinueButton() {
        val canContinue = selectedAvatar != null && networkManager.isAvatarConfirmed
        binding.continueButton.visibility = if (selectedAvatar != null) View.VISIBLE else View.GONE
        binding.continueButton.isEnabled = canContinue
        binding.continueButton.alpha = if (canContinue) 1.0f else 0.5f
    }

    private fun setupListeners() {
        binding.continueButton.setOnClickListener {
            proceedToWaitingRoom()
        }
    }

    private fun encodeAvatarJpeg(source: Bitmap): ByteArray {
        val scaled = Bitmap.createScaledBitmap(source, 512, 512, true)
        val clean = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(clean)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        val outputStream = ByteArrayOutputStream()
        clean.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        if (scaled != source) scaled.recycle()
        clean.recycle()
        return outputStream.toByteArray()
    }

    private fun proceedToWaitingRoom() {
        val imageData = capturedBitmap?.let { encodeAvatarJpeg(it) }
            ?: resources.openRawResource(R.raw.avatar_stub).use { it.readBytes() }

        val transferId = 2
        val imageGuid = java.util.UUID.randomUUID().toString()
        networkManager.sendImageProfileImage(imageData, imageGuid, transferId, args.playerName)

        // Navigate to waiting room
        val action = AvatarSelectionFragmentDirections.actionAvatarSelectionToWaitingRoom()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        networkManager.onAvatarsChanged = null
        networkManager.onAvatarRequestResponse = null
        _binding = null
    }
}
