package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter(
    private val scheduleItems: MutableList<ScheduleItem>
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    class ScheduleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val courseCode: TextView = view.findViewById(R.id.courseCode)
        val courseName: TextView = view.findViewById(R.id.courseName)
        val timeRange: TextView = view.findViewById(R.id.timeRange)
        val location: TextView = view.findViewById(R.id.location)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val item = scheduleItems[position]

        holder.courseCode.text = item.courseCode
        holder.courseName.text = item.courseName
        holder.timeRange.text = "${item.startTime} - ${item.endTime}"
        holder.location.text = "${item.building} ${item.room}"
    }

    override fun getItemCount() = scheduleItems.size

    fun updateSchedule(newItems: List<ScheduleItem>) {
        scheduleItems.clear()
        scheduleItems.addAll(newItems)
        notifyDataSetChanged()
    }
}