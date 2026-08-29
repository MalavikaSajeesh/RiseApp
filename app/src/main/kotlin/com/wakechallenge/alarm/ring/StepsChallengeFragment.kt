package com.wakechallenge.alarm.ring

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wakechallenge.alarm.data.StepsGoalConfig
import com.wakechallenge.alarm.databinding.FragmentChallengeStepsBinding
import com.wakechallenge.alarm.util.MotionRepCounter

class StepsChallengeFragment : Fragment() {

    private var _binding: FragmentChallengeStepsBinding? = null
    private val binding get() = _binding!!

    private lateinit var listener: ChallengeListener
    private lateinit var config: StepsGoalConfig
    private var sensorManager: SensorManager? = null
    private var counter: MotionRepCounter? = null
    private var currentSteps = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChallengeStepsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listener = requireActivity() as ChallengeListener
        config = StepsGoalConfig.from(requireArguments().getString(ARG_CONFIG_JSON) ?: "{}")

        binding.textInstruction.text = "Walk ${config.targetSteps} steps"
        updateCountText()
        binding.progressBar.max = config.targetSteps

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        counter = MotionRepCounter.forSteps { onStepDetected() }
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) sensorManager?.registerListener(counter, accel, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun onStepDetected() {
        currentSteps++
        requireActivity().runOnUiThread {
            updateCountText()
            binding.progressBar.progress = currentSteps
            if (currentSteps >= config.targetSteps) {
                sensorManager?.unregisterListener(counter)
                listener.onChallengeCompleted()
            }
        }
    }

    private fun updateCountText() {
        binding.textCount.text = "$currentSteps / ${config.targetSteps}"
    }

    override fun onDestroyView() {
        sensorManager?.unregisterListener(counter)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONFIG_JSON = "config_json"
        fun newInstance(configJson: String) = StepsChallengeFragment().apply {
            arguments = Bundle().apply { putString(ARG_CONFIG_JSON, configJson) }
        }
    }
}
