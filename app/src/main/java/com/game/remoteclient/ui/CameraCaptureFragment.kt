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
import com.game.remoteclient.databinding.FragmentCameraCaptureBinding
import com.game.remoteclient.utils.PermissionHelper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureFragment : Fragment() {

    private var _binding: FragmentCameraCaptureBinding? = null
    private val binding get() = _binding!!

    private val args: CameraCaptureFragmentArgs by navArgs()

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
        setupListeners()

        if (PermissionHelper.hasCameraPermission(requireContext())) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(PermissionHelper.CAMERA_PERMISSION)
        }
    }

    private fun setupListeners() {
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
        binding.cameraPreview.visibility = View.VISIBLE
        binding.capturedImage.visibility = View.GONE
        binding.captureButton.visibility = View.VISIBLE
        binding.retakeButton.visibility = View.GONE
        binding.confirmButton.visibility = View.GONE
    }

    private fun confirmPhoto() {
        val bitmap = capturedBitmap ?: return

        val file = File(requireContext().cacheDir, PHOTO_FILENAME)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val action = CameraCaptureFragmentDirections.actionCameraCaptureToAvatarSelection(
            playerName = args.playerName
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        const val PHOTO_FILENAME = "avatar_photo.jpg"
    }
}
