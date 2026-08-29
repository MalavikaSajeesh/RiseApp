package com.wakechallenge.alarm.ring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wakechallenge.alarm.data.MathGoalConfig
import com.wakechallenge.alarm.databinding.FragmentChallengeMathBinding
import kotlin.random.Random

class MathChallengeFragment : Fragment() {

    private var _binding: FragmentChallengeMathBinding? = null
    private val binding get() = _binding!!

    private lateinit var listener: ChallengeListener
    private lateinit var config: MathGoalConfig
    private var problemIndex = 0
    private var currentAnswer = 0
    private var wrongStreak = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChallengeMathBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listener = requireActivity() as ChallengeListener
        config = MathGoalConfig.from(requireArguments().getString(ARG_CONFIG_JSON) ?: "{}")
        nextProblem()

        binding.buttonSubmit.setOnClickListener { checkAnswer() }
    }

    private fun nextProblem() {
        val (text, answer) = generateProblem(config.difficulty)
        currentAnswer = answer
        binding.textProblem.text = text
        binding.editAnswer.setText("")
        binding.textProgress.text = "Problem ${problemIndex + 1} of ${config.problemCount}"
    }

    private fun generateProblem(difficulty: Int): Pair<String, Int> {
        val range = when (difficulty) { 1 -> 10; 3 -> 100; else -> 30 }
        val a = Random.nextInt(2, range)
        val b = Random.nextInt(2, range)
        val ops = if (difficulty >= 2) listOf("+", "-", "x") else listOf("+", "-")
        return when (ops.random()) {
            "+" -> "$a + $b = ?" to (a + b)
            "-" -> "$a - $b = ?" to (a - b)
            else -> "$a x $b = ?" to (a * b)
        }
    }

    private fun checkAnswer() {
        val entered = binding.editAnswer.text.toString().toIntOrNull()
        if (entered == currentAnswer) {
            problemIndex++
            wrongStreak = 0
            if (problemIndex >= config.problemCount) {
                listener.onChallengeCompleted()
            } else {
                nextProblem()
            }
        } else {
            wrongStreak++
            binding.editAnswer.error = "Not quite"
            if (wrongStreak >= 3) listener.onChallengeStruggling()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONFIG_JSON = "config_json"
        fun newInstance(configJson: String) = MathChallengeFragment().apply {
            arguments = Bundle().apply { putString(ARG_CONFIG_JSON, configJson) }
        }
    }
}
