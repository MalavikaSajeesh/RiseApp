package com.wakechallenge.alarm.ring

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.wakechallenge.alarm.data.ReciteGoalConfig
import com.wakechallenge.alarm.databinding.FragmentChallengeReciteBinding
import com.wakechallenge.alarm.util.TextMatchUtil
import java.util.Locale

class ReciteChallengeFragment : Fragment() {

    private var _binding: FragmentChallengeReciteBinding? = null
    private val binding get() = _binding!!

    private lateinit var listener: ChallengeListener
    private lateinit var config: ReciteGoalConfig
    private var failedAttempts = 0

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull() ?: ""
        handleSpokenText(text)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChallengeReciteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listener = requireActivity() as ChallengeListener
        config = ReciteGoalConfig.from(requireArguments().getString(ARG_CONFIG_JSON) ?: "{}")
        binding.textTarget.text = "Recite:\n\n\u201c${config.verseText}\u201d"

        binding.buttonSpeak.setOnClickListener { startListening() }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            listener.onChallengeStruggling()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechLauncher.launch(intent)
    }

    private fun handleSpokenText(text: String) {
        binding.textHeard.text = "Heard: \u201c$text\u201d"
        val score = TextMatchUtil.similarity(config.verseText, text)
        if (score >= config.matchThreshold) {
            listener.onChallengeCompleted()
        } else {
            failedAttempts++
            binding.textHeard.append("\n\nNot quite — try again, speak clearly.")
            if (failedAttempts >= 2) listener.onChallengeStruggling()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONFIG_JSON = "config_json"
        fun newInstance(configJson: String) = ReciteChallengeFragment().apply {
            arguments = Bundle().apply { putString(ARG_CONFIG_JSON, configJson) }
        }
    }
}
