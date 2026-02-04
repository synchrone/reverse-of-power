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
import com.game.remoteclient.databinding.FragmentAvatarSelectionBinding
import com.game.remoteclient.utils.PermissionHelper
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AvatarSelectionFragment : Fragment() {

    private var _binding: FragmentAvatarSelectionBinding? = null
    private val binding get() = _binding!!

    private val args: AvatarSelectionFragmentArgs by navArgs()
    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var capturedBitmap: Bitmap? = null
    private var capturedImageGuid: String = UUID.randomUUID().toString()
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
        }
    }

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

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupAvatarList()
        setupListeners()
        observeAvatars()
        initializeCamera()
    }

    private fun initializeCamera() {
        if (PermissionHelper.hasCameraPermission(requireContext())) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(PermissionHelper.CAMERA_PERMISSION)
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
        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        binding.retakeButton.setOnClickListener {
            retakePhoto()
        }

        binding.continueButton.setOnClickListener {
            proceedToWaitingRoom()
        }
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
        binding.avatarImage.visibility = View.VISIBLE
        binding.avatarImage.setImageBitmap(capturedBitmap)
        binding.captureButton.visibility = View.GONE
        binding.retakeButton.visibility = View.VISIBLE
    }

    private fun retakePhoto() {
        capturedBitmap = null
        binding.cameraPreview.visibility = View.VISIBLE
        binding.avatarImage.visibility = View.GONE
        binding.captureButton.visibility = View.VISIBLE
        binding.retakeButton.visibility = View.GONE
    }

    /**
     * Encode bitmap as clean baseline JPEG (JFIF 1.01, YCbCr, no EXIF/metadata)
     * Output: 512x512 pixels
     */
    private fun encodeAvatarJpeg(source: Bitmap): ByteArray {
        // Scale to 512x512
        val scaled = Bitmap.createScaledBitmap(source, 512, 512, true)

        // Create a fresh ARGB_8888 bitmap to strip any metadata
        val clean = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(clean)
        canvas.drawBitmap(scaled, 0f, 0f, null)

        // Encode as baseline JPEG (Android's default encoder produces baseline JFIF)
        val outputStream = ByteArrayOutputStream()
        clean.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

        // Cleanup
        if (scaled != source) scaled.recycle()
        clean.recycle()

        return outputStream.toByteArray()
    }

    val STUB_AVATAR_BASE64 = "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"

    private fun proceedToWaitingRoom() {
        val imageData = capturedBitmap?.let { encodeAvatarJpeg(it) }
            ?: android.util.Base64.decode(STUB_AVATAR_BASE64, android.util.Base64.DEFAULT)

        // dunno why is it being sent twice by official client, but something to try i guess. Maybe it should just be also before profile data, not only after?

        val transferId = (Math.random() * 200).toInt()
        val imageGuid = "7551b16c-ac64-49db-86cd-ea2f3c446a04"
        networkManager.sendImage(imageData, imageGuid, transferId)
        networkManager.sendPlayerProfile(args.playerName)
        networkManager.sendImage(imageData, imageGuid, transferId)

        // Navigate to waiting room
        val action = AvatarSelectionFragmentDirections.actionAvatarSelectionToWaitingRoom()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        networkManager.onAvatarsChanged = null
        networkManager.onAvatarRequestResponse = null
        cameraExecutor.shutdown()
        _binding = null
    }
}
