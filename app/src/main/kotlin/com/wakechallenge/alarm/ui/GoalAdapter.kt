package com.wakechallenge.alarm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wakechallenge.alarm.data.GoalEntity
import com.wakechallenge.alarm.data.GoalType
import com.wakechallenge.alarm.databinding.ItemGoalBinding

class GoalAdapter(private val onClick: (GoalEntity) -> Unit) : ListAdapter<GoalEntity, GoalAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemGoalBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGoalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val goal = getItem(position)
        holder.binding.textName.text = goal.name
        holder.binding.textType.text = labelFor(goal.type)
        holder.binding.root.setOnClickListener { onClick(goal) }
    }

    private fun labelFor(type: GoalType): String = when (type) {
        GoalType.PHOTO -> "Photo challenge"
        GoalType.STEPS -> "Steps challenge"
        GoalType.JUMPING_JACKS -> "Jumping jacks challenge"
        GoalType.RECITE -> "Recite challenge"
        GoalType.MATH -> "Math challenge"
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<GoalEntity>() {
            override fun areItemsTheSame(old: GoalEntity, new: GoalEntity) = old.id == new.id
            override fun areContentsTheSame(old: GoalEntity, new: GoalEntity) = old == new
        }
    }
}
