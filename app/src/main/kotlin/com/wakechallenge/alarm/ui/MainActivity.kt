package com.wakechallenge.alarm.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wakechallenge.alarm.R
import com.wakechallenge.alarm.alarm.AlarmScheduler
import com.wakechallenge.alarm.data.AppDatabase
import com.wakechallenge.alarm.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuGoals -> {
                startActivity(Intent(this, GoalLibraryActivity::class.java))
                true
            }
            R.id.menuMusicPool -> {
                startActivity(Intent(this, MusicPoolActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = AlarmAdapter(
            onToggle = { alarm, checked ->
                lifecycleScope.launch {
                    val db = AppDatabase.get(applicationContext)
                    db.alarmDao().setEnabled(alarm.id, checked)
                    val updated = alarm.copy(enabled = checked)
                    if (checked) AlarmScheduler.schedule(this@MainActivity, updated)
                    else AlarmScheduler.cancel(this@MainActivity, alarm.id)
                }
            },
            onClick = { alarm ->
                startActivity(Intent(this, AlarmEditActivity::class.java).apply {
                    putExtra(AlarmEditActivity.EXTRA_ALARM_ID, alarm.id)
                })
            }
        )
        binding.recyclerAlarms.layoutManager = LinearLayoutManager(this)
        binding.recyclerAlarms.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AlarmEditActivity::class.java))
        }

        requestExactAlarmPermissionIfNeeded()
        requestNotificationPermissionIfNeeded()
        observeAlarms()
    }

    private fun observeAlarms() {
        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
            db.alarmDao().observeAll().collectLatest { alarms ->
                val combined = alarms.map { alarm ->
                    val primary = db.goalDao().getById(alarm.primaryGoalId)
                    val secondary = alarm.secondaryGoalId?.let { db.goalDao().getById(it) }
                    AlarmWithGoals(alarm, primary, secondary)
                }
                adapter.submitList(combined)
                binding.textEmpty.visibility = if (combined.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
            }
        }
    }
}
