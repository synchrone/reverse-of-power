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
        binding.continueButton.visibility = if (selectedAvatar != null) View.VISIBLE else View.GONE
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

    private fun proceedToWaitingRoom() {
        // Send player profile with chosen name and avatar
        networkManager.sendPlayerProfile(args.playerName)

        // Send captured image if available
        capturedBitmap?.let { bitmap ->
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val imageData = outputStream.toByteArray()
            networkManager.sendImage(imageData, capturedImageGuid)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
        }

        // Navigate to waiting room
        val action = AvatarSelectionFragmentDirections.actionAvatarSelectionToWaitingRoom()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        networkManager.onAvatarsChanged = null
        cameraExecutor.shutdown()
        _binding = null
    }
}
