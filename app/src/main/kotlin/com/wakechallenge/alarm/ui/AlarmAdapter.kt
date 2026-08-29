package com.wakechallenge.alarm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wakechallenge.alarm.data.AlarmEntity
import com.wakechallenge.alarm.data.GoalEntity
import com.wakechallenge.alarm.databinding.ItemAlarmBinding
import java.util.Calendar

class AlarmAdapter(
    private val onToggle: (AlarmEntity, Boolean) -> Unit,
    private val onClick: (AlarmEntity) -> Unit
) : ListAdapter<AlarmWithGoals, AlarmAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAlarmBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val alarm = item.alarm
        val hour12 = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
        val ampm = if (alarm.hour < 12) "AM" else "PM"
        holder.binding.textTime.text = String.format("%d:%02d %s", hour12, alarm.minute, ampm)

        holder.binding.textDays.text = if (alarm.isRepeating()) {
            val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            names.filterIndexed { idx, _ -> alarm.repeatsOn(Calendar.SUNDAY + idx) }.joinToString(" ")
        } else {
            "One-time" + if (alarm.label.isNotBlank()) " · ${alarm.label}" else ""
        }

        val primaryName = item.primaryGoal?.name ?: "?"
        val secondaryName = item.secondaryGoal?.name
        holder.binding.textGoals.text = if (secondaryName != null) {
            "Primary: $primaryName · Backup: $secondaryName"
        } else {
            "Primary: $primaryName"
        }

        holder.binding.switchEnabled.setOnCheckedChangeListener(null)
        holder.binding.switchEnabled.isChecked = alarm.enabled
        holder.binding.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(alarm, checked) }

        holder.binding.root.setOnClickListener { onClick(alarm) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AlarmWithGoals>() {
            override fun areItemsTheSame(old: AlarmWithGoals, new: AlarmWithGoals) = old.alarm.id == new.alarm.id
            override fun areContentsTheSame(old: AlarmWithGoals, new: AlarmWithGoals) = old == new
        }
    }
}

data class AlarmWithGoals(
    val alarm: AlarmEntity,
    val primaryGoal: GoalEntity?,
    val secondaryGoal: GoalEntity?
)
