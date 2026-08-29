package com.wakechallenge.alarm.ring

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wakechallenge.alarm.alarm.AlarmRingingService
import com.wakechallenge.alarm.alarm.AlarmScheduler
import com.wakechallenge.alarm.data.AppDatabase
import com.wakechallenge.alarm.data.GoalEntity
import com.wakechallenge.alarm.data.GoalType
import com.wakechallenge.alarm.databinding.ActivityAlarmRingBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmRingActivity : AppCompatActivity(), ChallengeListener {

    private lateinit var binding: ActivityAlarmRingBinding
    private var ringingService: AlarmRingingService? = null
    private var bound = false
    private var alarmId: Long = -1

    private var primaryGoal: GoalEntity? = null
    private var secondaryGoal: GoalEntity? = null
    private var showingPrimary = true

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AlarmRingingService.LocalBinder
            ringingService = binder.getService()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            ringingService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The alarm can only be dismissed by completing a challenge, not by backing out.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op, intentionally blocked */ }
        })

        alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        binding.textTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Calendar.getInstance().time)

        requestRuntimePermissionsIfNeeded()
        loadGoalsAndShowPrimary()

        binding.buttonSwitchBackup.setOnClickListener { switchToSecondary() }

        bindService(Intent(this, AlarmRingingService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.CAMERA
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
        }
    }

    private fun loadGoalsAndShowPrimary() {
        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
            val alarm = db.alarmDao().getById(alarmId) ?: return@launch
            primaryGoal = db.goalDao().getById(alarm.primaryGoalId)
            secondaryGoal = alarm.secondaryGoalId?.let { db.goalDao().getById(it) }
            showGoal(primaryGoal, isPrimary = true)
        }
    }

    private fun showGoal(goal: GoalEntity?, isPrimary: Boolean) {
        if (goal == null) return
        showingPrimary = isPrimary
        binding.textChallengeLabel.text = if (isPrimary) "Primary challenge: ${goal.name}" else "Backup challenge: ${goal.name}"
        binding.buttonSwitchBackup.visibility = View.GONE

        val fragment: Fragment = when (goal.type) {
            GoalType.PHOTO -> PhotoChallengeFragment.newInstance(goal.configJson)
            GoalType.STEPS -> StepsChallengeFragment.newInstance(goal.configJson)
            GoalType.JUMPING_JACKS -> JumpingJacksChallengeFragment.newInstance(goal.configJson)
            GoalType.RECITE -> ReciteChallengeFragment.newInstance(goal.configJson)
            GoalType.MATH -> MathChallengeFragment.newInstance(goal.configJson)
        }
        supportFragmentManager.beginTransaction()
            .replace(binding.challengeContainer.id, fragment)
            .commit()
    }

    private fun switchToSecondary() {
        val goal = secondaryGoal ?: return
        showGoal(goal, isPrimary = false)
    }

    override fun onChallengeCompleted() {
        ringingService?.completeChallenge()
        finish()
    }

    override fun onChallengeStruggling() {
        if (showingPrimary && secondaryGoal != null) {
            binding.buttonSwitchBackup.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
