package com.wakechallenge.alarm.ring

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.wakechallenge.alarm.data.PhotoGoalConfig
import com.wakechallenge.alarm.databinding.FragmentChallengePhotoBinding
import com.wakechallenge.alarm.util.ImageHashUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class PhotoChallengeFragment : Fragment() {

    private var _binding: FragmentChallengePhotoBinding? = null
    private val binding get() = _binding!!

    private lateinit var config: PhotoGoalConfig
    private lateinit var listener: ChallengeListener
    private var imageCapture: ImageCapture? = null
    private var capturedPath: String? = null
    private var failedAttempts = 0

    private val sharpnessMinScore = 6.0 // below this, we treat the photo as too blurry

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChallengePhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listener = requireActivity() as ChallengeListener
        val configJson = requireArguments().getString(ARG_CONFIG_JSON) ?: "{}"
        config = PhotoGoalConfig.from(configJson)
        binding.textPrompt.text = config.prompt

        startCamera()

        binding.buttonCapture.setOnClickListener { capturePhoto() }
        binding.buttonRetake.setOnClickListener { resetToCameraView() }
        binding.buttonVerify.setOnClickListener { verifyPhoto() }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                // Camera unavailable — user can still fall back to the backup challenge.
                listener.onChallengeStruggling()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        val fileName = "wakechallenge_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        val file = File(requireContext().cacheDir, fileName)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedPath = file.absolutePath
                    showCapturedPreview(file.absolutePath)
                }
                override fun onError(exception: ImageCaptureException) {
                    listener.onChallengeStruggling()
                }
            }
        )
    }

    private fun showCapturedPreview(path: String) {
        binding.previewView.visibility = View.GONE
        binding.imageCaptured.visibility = View.VISIBLE
        binding.imageCaptured.setImageBitmap(BitmapFactory.decodeFile(path))
        binding.buttonCapture.visibility = View.GONE
        binding.buttonRetake.visibility = View.VISIBLE
        binding.buttonVerify.visibility = View.VISIBLE
    }

    private fun resetToCameraView() {
        binding.previewView.visibility = View.VISIBLE
        binding.imageCaptured.visibility = View.GONE
        binding.buttonCapture.visibility = View.VISIBLE
        binding.buttonRetake.visibility = View.GONE
        binding.buttonVerify.visibility = View.GONE
        binding.textFeedback.visibility = View.GONE
    }

    private fun verifyPhoto() {
        val path = capturedPath ?: return
        val bitmap = ImageHashUtil.decodeSampled(path) ?: return

        val sharpness = ImageHashUtil.sharpnessScore(bitmap)
        if (sharpness < sharpnessMinScore) {
            showFeedback(getString(com.wakechallenge.alarm.R.string.too_blurry))
            registerFailure()
            return
        }

        val refPath = config.referenceImagePath
        val isMatch = if (refPath.isNullOrEmpty()) {
            true // no reference was saved — clarity check alone is enough
        } else {
            val refBitmap = ImageHashUtil.decodeSampled(refPath)
            if (refBitmap == null) {
                true
            } else {
                val distance = ImageHashUtil.hammingDistance(
                    ImageHashUtil.averageHash(bitmap),
                    ImageHashUtil.averageHash(refBitmap)
                )
                distance <= config.similarityThreshold
            }
        }

        if (isMatch) {
            listener.onChallengeCompleted()
        } else {
            showFeedback(getString(com.wakechallenge.alarm.R.string.no_match_try_again))
            registerFailure()
        }
    }

    private fun registerFailure() {
        failedAttempts++
        if (failedAttempts >= 2) {
            listener.onChallengeStruggling()
        }
    }

    private fun showFeedback(text: String) {
        binding.textFeedback.text = text
        binding.textFeedback.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONFIG_JSON = "config_json"
        fun newInstance(configJson: String) = PhotoChallengeFragment().apply {
            arguments = Bundle().apply { putString(ARG_CONFIG_JSON, configJson) }
        }
    }
}
