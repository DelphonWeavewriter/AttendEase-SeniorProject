package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter(
    private val scheduleItems: MutableList<ScheduleItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_CLASS = 0
        private const val VIEW_TYPE_FINAL = 1
    }

    class ScheduleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val courseCode: TextView = view.findViewById(R.id.courseCode)
        val courseName: TextView = view.findViewById(R.id.courseName)
        val timeRange: TextView = view.findViewById(R.id.timeRange)
        val location: TextView = view.findViewById(R.id.location)
    }

    class FinalExamViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val courseCode: TextView = view.findViewById(R.id.courseCode)
        val courseName: TextView = view.findViewById(R.id.courseName)
        val timeRange: TextView = view.findViewById(R.id.timeRange)
        val location: TextView = view.findViewById(R.id.location)
    }

    override fun getItemViewType(position: Int): Int {
        return if (scheduleItems[position].isFinalExam) VIEW_TYPE_FINAL else VIEW_TYPE_CLASS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_FINAL) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_final_exam, parent, false)
            FinalExamViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_schedule, parent, false)
            ScheduleViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = scheduleItems[position]
        if (holder is FinalExamViewHolder) {
            holder.courseCode.text = item.courseCode
            holder.courseName.text = "Final Exam: ${item.courseName}"
            holder.timeRange.text = "${item.startTime} - ${item.endTime}"
            holder.location.text = "${item.building} ${item.room}"
        } else if (holder is ScheduleViewHolder) {
            holder.courseCode.text = item.courseCode
            holder.courseName.text = item.courseName
            holder.timeRange.text = "${item.startTime} - ${item.endTime}"
            holder.location.text = "${item.building} ${item.room}"
        }
    }

    override fun getItemCount() = scheduleItems.size

    fun updateSchedule(newItems: List<ScheduleItem>) {
        scheduleItems.clear()
        scheduleItems.addAll(newItems)
        notifyDataSetChanged()
    }
}