package com.wakechallenge.alarm.ui

import android.app.AlertDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.wakechallenge.alarm.alarm.AlarmScheduler
import com.wakechallenge.alarm.data.AlarmEntity
import com.wakechallenge.alarm.data.AppDatabase
import com.wakechallenge.alarm.data.GoalEntity
import com.wakechallenge.alarm.data.SoundMode
import com.wakechallenge.alarm.databinding.ActivityAlarmEditBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private var alarmId: Long = -1
    private var existingAlarm: AlarmEntity? = null

    private var allGoals: List<GoalEntity> = emptyList()
    private var selectedPrimary: GoalEntity? = null
    private var selectedSecondary: GoalEntity? = null

    private var selectedSoundUri: String? = null
    private var selectedSoundName: String = "Default alarm sound"

    private lateinit var dayChips: List<Chip>

    private val ringtonePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            selectedSoundUri = uri.toString()
            selectedSoundName = RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "Custom sound"
        } else {
            selectedSoundUri = null
            selectedSoundName = "Default alarm sound"
        }
        updateSoundButtonLabel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dayChips = listOf(
            binding.chipSun, binding.chipMon, binding.chipTue, binding.chipWed,
            binding.chipThu, binding.chipFri, binding.chipSat
        )

        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)

        binding.buttonPickPrimary.setOnClickListener { showGoalPicker(isPrimary = true) }
        binding.buttonPickSecondary.setOnClickListener { showGoalPicker(isPrimary = false) }
        binding.buttonSave.setOnClickListener { save() }
        binding.buttonDelete.setOnClickListener { delete() }

        binding.radioGroupSoundMode.setOnCheckedChangeListener { _, checkedId ->
            val isPool = checkedId == binding.radioRandomPool.id
            binding.buttonPickRingtone.visibility = if (isPool) android.view.View.GONE else android.view.View.VISIBLE
            binding.buttonManagePool.visibility = if (isPool) android.view.View.VISIBLE else android.view.View.GONE
        }
        binding.buttonPickRingtone.setOnClickListener { openRingtonePicker() }
        binding.buttonManagePool.setOnClickListener {
            startActivity(Intent(this, MusicPoolActivity::class.java))
        }

        loadData()
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getActualDefaultRingtoneUri(this@AlarmEditActivity, RingtoneManager.TYPE_ALARM)
            )
            selectedSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun updateSoundButtonLabel() {
        binding.buttonPickRingtone.text = "Sound: $selectedSoundName"
    }

    private fun loadData() {
        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
            allGoals = db.goalDao().observeAll().first()

            if (alarmId != -1L) {
                existingAlarm = db.alarmDao().getById(alarmId)
                existingAlarm?.let { alarm ->
                    binding.timePicker.hour = alarm.hour
                    binding.timePicker.minute = alarm.minute
                    binding.editLabel.setText(alarm.label)
                    binding.checkVibrate.isChecked = alarm.vibrate
                    for (i in dayChips.indices) {
                        dayChips[i].isChecked = alarm.repeatsOn(Calendar.SUNDAY + i)
                    }
                    selectedPrimary = allGoals.firstOrNull { it.id == alarm.primaryGoalId }
                    selectedSecondary = alarm.secondaryGoalId?.let { id -> allGoals.firstOrNull { it.id == id } }
                    binding.buttonDelete.visibility = android.view.View.VISIBLE

                    selectedSoundUri = alarm.soundUri
                    selectedSoundName = alarm.soundDisplayName
                    binding.seekRingVolume.progress = alarm.ringVolume
                    if (alarm.soundMode == SoundMode.RANDOM_FROM_POOL) {
                        binding.radioGroupSoundMode.check(binding.radioRandomPool.id)
                    } else {
                        binding.radioGroupSoundMode.check(binding.radioSpecificSound.id)
                    }
                    updateSoundButtonLabel()
                }
            } else {
                binding.seekRingVolume.progress = 100
                updateSoundButtonLabel()
            }
            updateGoalButtonLabels()
        }
    }

    private fun showGoalPicker(isPrimary: Boolean) {
        if (allGoals.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No challenges yet")
                .setMessage("Create at least one challenge in the challenge library first.")
                .setPositiveButton("Open library") { _, _ ->
                    startActivity(android.content.Intent(this, GoalLibraryActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val names = (if (!isPrimary) listOf("None") else emptyList()) + allGoals.map { it.name }
        AlertDialog.Builder(this)
            .setTitle(if (isPrimary) "Choose primary challenge" else "Choose backup challenge")
            .setItems(names.toTypedArray()) { _, index ->
                if (!isPrimary && index == 0) {
                    selectedSecondary = null
                } else {
                    val goalIndex = if (!isPrimary) index - 1 else index
                    val goal = allGoals[goalIndex]
                    if (isPrimary) selectedPrimary = goal else selectedSecondary = goal
                }
                updateGoalButtonLabels()
            }
            .show()
    }

    private fun updateGoalButtonLabels() {
        binding.buttonPickPrimary.text = selectedPrimary?.name ?: "Choose primary challenge"
        binding.buttonPickSecondary.text = selectedSecondary?.name ?: "Choose backup challenge (optional)"
    }

    private fun save() {
        val primary = selectedPrimary
        if (primary == null) {
            AlertDialog.Builder(this).setMessage("Pick a primary challenge first.").setPositiveButton("OK", null).show()
            return
        }
        var mask = 0
        for (i in dayChips.indices) {
            if (dayChips[i].isChecked) mask = mask or AlarmEntity.dayBit(Calendar.SUNDAY + i)
        }

        val alarm = AlarmEntity(
            id = existingAlarm?.id ?: 0,
            hour = binding.timePicker.hour,
            minute = binding.timePicker.minute,
            daysMask = mask,
            enabled = true,
            label = binding.editLabel.text.toString(),
            primaryGoalId = primary.id,
            secondaryGoalId = selectedSecondary?.id,
            vibrate = binding.checkVibrate.isChecked,
            soundMode = if (binding.radioGroupSoundMode.checkedRadioButtonId == binding.radioRandomPool.id)
                SoundMode.RANDOM_FROM_POOL else SoundMode.SYSTEM_RINGTONE,
            soundUri = selectedSoundUri,
            soundDisplayName = selectedSoundName,
            ringVolume = binding.seekRingVolume.progress.coerceIn(5, 100)
        )

        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
            val newId = db.alarmDao().upsert(alarm)
            val saved = alarm.copy(id = newId)
            AlarmScheduler.schedule(this@AlarmEditActivity, saved)
            finish()
        }
    }

    private fun delete() {
        val alarm = existingAlarm ?: return
        lifecycleScope.launch {
            AppDatabase.get(applicationContext).alarmDao().delete(alarm)
            AlarmScheduler.cancel(this@AlarmEditActivity, alarm.id)
            finish()
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }
}
