package com.game.remoteclient.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.game.protocol.ServerAvatarStatusMessage
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentCameraCaptureBinding
import com.game.remoteclient.utils.PermissionHelper
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureFragment : Fragment() {

    private var _binding: FragmentCameraCaptureBinding? = null
    private val binding get() = _binding!!

    private val args: CameraCaptureFragmentArgs by navArgs()
    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var capturedBitmap: Bitmap? = null
    private var photoConfirmed = false

    private lateinit var avatarAdapter: AvatarAdapter
    private var selectedAvatar: ServerAvatarStatusMessage? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.camera_permission_required,
                Toast.LENGTH_SHORT
            ).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupCameraListeners()
        setupAvatarList()
        setupContinueButton()
        observeAvatars()

        if (PermissionHelper.hasCameraPermission(requireContext())) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(PermissionHelper.CAMERA_PERMISSION)
        }
    }

    // --- Camera ---

    private fun setupCameraListeners() {
        binding.captureButton.setOnClickListener { capturePhoto() }
        binding.retakeButton.setOnClickListener { retakePhoto() }
        binding.confirmButton.setOnClickListener { confirmPhoto() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Camera failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    capturedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    image.close()

                    showCapturedPhoto()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        "Photo capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun showCapturedPhoto() {
        binding.cameraPreview.visibility = View.GONE
        binding.capturedImage.visibility = View.VISIBLE
        binding.capturedImage.setImageBitmap(capturedBitmap)
        binding.captureButton.visibility = View.GONE
        binding.retakeButton.visibility = View.VISIBLE
        binding.confirmButton.visibility = View.VISIBLE
    }

    private fun retakePhoto() {
        capturedBitmap = null
        photoConfirmed = false
        binding.cameraPreview.visibility = View.VISIBLE
        binding.capturedImage.visibility = View.GONE
        binding.captureButton.visibility = View.VISIBLE
        binding.retakeButton.visibility = View.GONE
        binding.confirmButton.visibility = View.GONE
        updateContinueButton()
    }

    private fun confirmPhoto() {
        photoConfirmed = true
        binding.retakeButton.visibility = View.VISIBLE
        binding.confirmButton.visibility = View.GONE
        updateContinueButton()
    }

    // --- Avatar Selection ---

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
                if (_binding == null) return@runOnUiThread
                updateAvatarList()
            }
        }

        networkManager.onAvatarRequestResponse = { response ->
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                if (response.AvatarID == selectedAvatar?.AvatarID) {
                    updateContinueButton()
                    if (!response.Available) {
                        Toast.makeText(requireContext(), "Avatar unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

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
        val avatarReady = selectedAvatar != null && networkManager.isAvatarConfirmed
        binding.continueButton.visibility = if (selectedAvatar != null) View.VISIBLE else View.GONE
        binding.continueButton.isEnabled = avatarReady
        binding.continueButton.alpha = if (avatarReady) 1.0f else 0.5f
    }

    private fun setupContinueButton() {
        binding.continueButton.setOnClickListener {
            proceedToWaitingRoom()
        }
    }

    // --- Proceed ---

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

        val action = CameraCaptureFragmentDirections.actionCameraCaptureToWaitingRoom()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        networkManager.onAvatarsChanged = null
        networkManager.onAvatarRequestResponse = null
        _binding = null
    }

    companion object {
        const val PHOTO_FILENAME = "avatar_photo.jpg"
    }
}
