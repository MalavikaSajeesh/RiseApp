package com.wakechallenge.alarm.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.wakechallenge.alarm.data.AppDatabase
import com.wakechallenge.alarm.data.GoalEntity
import com.wakechallenge.alarm.data.GoalType
import com.wakechallenge.alarm.data.JumpingJacksGoalConfig
import com.wakechallenge.alarm.data.MathGoalConfig
import com.wakechallenge.alarm.data.PhotoGoalConfig
import com.wakechallenge.alarm.data.ReciteGoalConfig
import com.wakechallenge.alarm.data.StepsGoalConfig
import com.wakechallenge.alarm.databinding.ActivityEditGoalBinding
import kotlinx.coroutines.launch
import java.io.File

class EditGoalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditGoalBinding
    private var goalId: Long = -1
    private var existingGoal: GoalEntity? = null
    private var referencePhotoPath: String? = null
    private var pendingPhotoFile: File? = null

    private val typeOptions = listOf(
        GoalType.PHOTO to "Photo",
        GoalType.STEPS to "Steps",
        GoalType.JUMPING_JACKS to "Jumping jacks",
        GoalType.RECITE to "Recite",
        GoalType.MATH to "Math"
    )

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingPhotoFile?.let { file ->
                referencePhotoPath = file.absolutePath
                showReferencePreview(file.absolutePath)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditGoalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L)

        binding.spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeOptions.map { it.second })
        binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                showSectionFor(typeOptions[position].first)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerDifficulty.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Easy", "Medium", "Hard"))

        binding.seekSteps.setOnSeekBarChangeListener(seekListener { v -> binding.textStepsValue.text = "Target: ${v.coerceAtLeast(5)} steps" })
        binding.seekJJ.setOnSeekBarChangeListener(seekListener { v -> binding.textJJValue.text = "Target: ${v.coerceAtLeast(3)} reps" })
        binding.seekProblemCount.setOnSeekBarChangeListener(seekListener { v -> binding.textProblemCount.text = "${v.coerceAtLeast(1)} problems" })

        binding.buttonTakeReference.setOnClickListener { launchCamera() }
        binding.buttonSaveGoal.setOnClickListener { save() }
        binding.buttonDeleteGoal.setOnClickListener { delete() }

        // Sensible defaults for a brand-new goal.
        binding.seekSteps.progress = 30
        binding.textStepsValue.text = "Target: 30 steps"
        binding.seekJJ.progress = 15
        binding.textJJValue.text = "Target: 15 reps"
        binding.seekProblemCount.progress = 3
        binding.textProblemCount.text = "3 problems"
        binding.seekPhotoStrictness.progress = 14

        loadIfEditing()
    }

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun showSectionFor(type: GoalType) {
        binding.sectionPhoto.visibility = if (type == GoalType.PHOTO) View.VISIBLE else View.GONE
        binding.sectionSteps.visibility = if (type == GoalType.STEPS) View.VISIBLE else View.GONE
        binding.sectionJumpingJacks.visibility = if (type == GoalType.JUMPING_JACKS) View.VISIBLE else View.GONE
        binding.sectionRecite.visibility = if (type == GoalType.RECITE) View.VISIBLE else View.GONE
        binding.sectionMath.visibility = if (type == GoalType.MATH) View.VISIBLE else View.GONE
    }

    private fun launchCamera() {
        val dir = File(filesDir, "reference_photos").apply { mkdirs() }
        val file = File(dir, "ref_${System.currentTimeMillis()}.jpg")
        pendingPhotoFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        takePictureLauncher.launch(intent)
    }

    private fun showReferencePreview(path: String) {
        binding.imageReferencePreview.visibility = View.VISIBLE
        binding.imageReferencePreview.setImageBitmap(BitmapFactory.decodeFile(path))
    }

    private fun loadIfEditing() {
        if (goalId == -1L) {
            binding.spinnerType.setSelection(0)
            showSectionFor(GoalType.PHOTO)
            return
        }
        lifecycleScope.launch {
            val goal = AppDatabase.get(applicationContext).goalDao().getById(goalId) ?: return@launch
            existingGoal = goal
            binding.editName.setText(goal.name)
            binding.buttonDeleteGoal.visibility = View.VISIBLE

            val typeIndex = typeOptions.indexOfFirst { it.first == goal.type }
            binding.spinnerType.setSelection(typeIndex)
            showSectionFor(goal.type)

            when (goal.type) {
                GoalType.PHOTO -> {
                    val cfg = PhotoGoalConfig.from(goal.configJson)
                    binding.editPhotoPrompt.setText(cfg.prompt)
                    binding.seekPhotoStrictness.progress = cfg.similarityThreshold
                    cfg.referenceImagePath?.let { path ->
                        referencePhotoPath = path
                        showReferencePreview(path)
                    }
                }
                GoalType.STEPS -> {
                    val cfg = StepsGoalConfig.from(goal.configJson)
                    binding.seekSteps.progress = cfg.targetSteps
                    binding.textStepsValue.text = "Target: ${cfg.targetSteps} steps"
                }
                GoalType.JUMPING_JACKS -> {
                    val cfg = JumpingJacksGoalConfig.from(goal.configJson)
                    binding.seekJJ.progress = cfg.targetReps
                    binding.textJJValue.text = "Target: ${cfg.targetReps} reps"
                }
                GoalType.RECITE -> {
                    val cfg = ReciteGoalConfig.from(goal.configJson)
                    binding.editVerseText.setText(cfg.verseText)
                }
                GoalType.MATH -> {
                    val cfg = MathGoalConfig.from(goal.configJson)
                    binding.spinnerDifficulty.setSelection((cfg.difficulty - 1).coerceIn(0, 2))
                    binding.seekProblemCount.progress = cfg.problemCount
                    binding.textProblemCount.text = "${cfg.problemCount} problems"
                }
            }
        }
    }

    private fun save() {
        val name = binding.editName.text.toString().ifBlank { "Untitled challenge" }
        val type = typeOptions[binding.spinnerType.selectedItemPosition].first

        val configJson = when (type) {
            GoalType.PHOTO -> PhotoGoalConfig.empty().apply {
                prompt = binding.editPhotoPrompt.text.toString().ifBlank { "Take a photo of your chosen subject" }
                referenceImagePath = referencePhotoPath
                similarityThreshold = binding.seekPhotoStrictness.progress.coerceAtLeast(4)
            }.toJson()
            GoalType.STEPS -> StepsGoalConfig.empty().apply {
                targetSteps = binding.seekSteps.progress.coerceAtLeast(5)
            }.toJson()
            GoalType.JUMPING_JACKS -> JumpingJacksGoalConfig.empty().apply {
                targetReps = binding.seekJJ.progress.coerceAtLeast(3)
            }.toJson()
            GoalType.RECITE -> ReciteGoalConfig.empty().apply {
                verseText = binding.editVerseText.text.toString()
            }.toJson()
            GoalType.MATH -> MathGoalConfig.empty().apply {
                difficulty = binding.spinnerDifficulty.selectedItemPosition + 1
                problemCount = binding.seekProblemCount.progress.coerceAtLeast(1)
            }.toJson()
        }

        val goal = GoalEntity(
            id = existingGoal?.id ?: 0,
            name = name,
            type = type,
            configJson = configJson
        )

        lifecycleScope.launch {
            AppDatabase.get(applicationContext).goalDao().upsert(goal)
            finish()
        }
    }

    private fun delete() {
        val goal = existingGoal ?: return
        lifecycleScope.launch {
            AppDatabase.get(applicationContext).goalDao().delete(goal)
            finish()
        }
    }

    companion object {
        const val EXTRA_GOAL_ID = "extra_goal_id"
    }
}
