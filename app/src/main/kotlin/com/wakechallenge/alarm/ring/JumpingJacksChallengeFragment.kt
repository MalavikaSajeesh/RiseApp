package com.wakechallenge.alarm.ring

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wakechallenge.alarm.data.JumpingJacksGoalConfig
import com.wakechallenge.alarm.databinding.FragmentChallengeJumpingJacksBinding
import com.wakechallenge.alarm.util.MotionRepCounter

class JumpingJacksChallengeFragment : Fragment() {

    private var _binding: FragmentChallengeJumpingJacksBinding? = null
    private val binding get() = _binding!!

    private lateinit var listener: ChallengeListener
    private lateinit var config: JumpingJacksGoalConfig
    private var sensorManager: SensorManager? = null
    private var counter: MotionRepCounter? = null
    private var currentReps = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChallengeJumpingJacksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listener = requireActivity() as ChallengeListener
        config = JumpingJacksGoalConfig.from(requireArguments().getString(ARG_CONFIG_JSON) ?: "{}")

        binding.textInstruction.text = "Do ${config.targetReps} jumping jacks"
        updateCountText()
        binding.progressBar.max = config.targetReps

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        counter = MotionRepCounter.forJumpingJacks { onRepDetected() }
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) sensorManager?.registerListener(counter, accel, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun onRepDetected() {
        currentReps++
        requireActivity().runOnUiThread {
            updateCountText()
            binding.progressBar.progress = currentReps
            if (currentReps >= config.targetReps) {
                sensorManager?.unregisterListener(counter)
                listener.onChallengeCompleted()
            }
        }
    }

    private fun updateCountText() {
        binding.textCount.text = "$currentReps / ${config.targetReps}"
    }

    override fun onDestroyView() {
        sensorManager?.unregisterListener(counter)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONFIG_JSON = "config_json"
        fun newInstance(configJson: String) = JumpingJacksChallengeFragment().apply {
            arguments = Bundle().apply { putString(ARG_CONFIG_JSON, configJson) }
        }
    }
}
