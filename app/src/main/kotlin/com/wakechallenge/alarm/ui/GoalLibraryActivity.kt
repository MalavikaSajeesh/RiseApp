package com.wakechallenge.alarm.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wakechallenge.alarm.data.AppDatabase
import com.wakechallenge.alarm.databinding.ActivityGoalLibraryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GoalLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGoalLibraryBinding
    private lateinit var adapter: GoalAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoalLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = GoalAdapter { goal ->
            startActivity(Intent(this, EditGoalActivity::class.java).apply {
                putExtra(EditGoalActivity.EXTRA_GOAL_ID, goal.id)
            })
        }
        binding.recyclerGoals.layoutManager = LinearLayoutManager(this)
        binding.recyclerGoals.adapter = adapter

        binding.fabAddGoal.setOnClickListener {
            startActivity(Intent(this, EditGoalActivity::class.java))
        }

        lifecycleScope.launch {
            AppDatabase.get(applicationContext).goalDao().observeAll().collectLatest {
                adapter.submitList(it)
            }
        }
    }
}
