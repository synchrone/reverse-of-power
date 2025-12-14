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
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentAvatarSelectionBinding
import com.game.remoteclient.models.GameServer
import com.game.remoteclient.models.Player
import com.game.remoteclient.network.NetworkManager
import com.game.remoteclient.utils.PermissionHelper
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AvatarSelectionFragment : Fragment() {

    private var _binding: FragmentAvatarSelectionBinding? = null
    private val binding get() = _binding!!

    private val args: AvatarSelectionFragmentArgs by navArgs()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var capturedBitmap: Bitmap? = null

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
        setupListeners()
    }

    private fun setupListeners() {
        binding.useCameraButton.setOnClickListener {
            if (PermissionHelper.hasCameraPermission(requireContext())) {
                startCamera()
            } else {
                cameraPermissionLauncher.launch(PermissionHelper.CAMERA_PERMISSION)
            }
        }

        binding.chooseAvatarButton.setOnClickListener {
            // For now, use a default avatar
            capturedBitmap = createDefaultAvatar()
            showCapturedPhoto()
        }

        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        binding.retakeButton.setOnClickListener {
            retakePhoto()
        }

        binding.continueButton.setOnClickListener {
            connectToServer()
        }
    }

    private fun startCamera() {
        binding.cameraPreview.visibility = View.VISIBLE
        binding.avatarImage.visibility = View.GONE
        binding.cameraControls.visibility = View.VISIBLE
        binding.actionButtons.visibility = View.GONE

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
        binding.cameraControls.visibility = View.VISIBLE
        binding.captureButton.visibility = View.GONE
        binding.retakeButton.visibility = View.VISIBLE
        binding.actionButtons.visibility = View.VISIBLE
        binding.useCameraButton.visibility = View.GONE
        binding.chooseAvatarButton.visibility = View.GONE
        binding.continueButton.visibility = View.VISIBLE
    }

    private fun retakePhoto() {
        capturedBitmap = null
        startCamera()
        binding.captureButton.visibility = View.VISIBLE
        binding.retakeButton.visibility = View.GONE
    }

    private fun createDefaultAvatar(): Bitmap {
        // Create a simple colored bitmap as placeholder
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.parseColor("#2196F3")
        canvas.drawCircle(100f, 100f, 100f, paint)
        return bitmap
    }

    private fun connectToServer() {
        val player = Player(
            name = args.playerName,
            avatarBitmap = capturedBitmap
        )

        val server = GameServer(args.serverIp)

        // Navigate to waiting room
        val action = AvatarSelectionFragmentDirections.actionAvatarSelectionToWaitingRoom(
            serverIp = args.serverIp,
            playerName = args.playerName
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
