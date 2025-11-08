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
        private const val VIEW_TYPE_EVENT = 2
    }

    class ScheduleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.courseName) // Reusing courseName for title
        val subtitle: TextView = view.findViewById(R.id.courseCode) // Reusing courseCode for subtitle
        val timeRange: TextView = view.findViewById(R.id.timeRange)
        val location: TextView = view.findViewById(R.id.location)
    }

    class FinalExamViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.courseName)
        val subtitle: TextView = view.findViewById(R.id.courseCode)
        val timeRange: TextView = view.findViewById(R.id.timeRange)
        val location: TextView = view.findViewById(R.id.location)
    }

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.courseName)
        val subtitle: TextView = view.findViewById(R.id.courseCode)
        val timeRange: TextView = view.findViewById(R.id.timeRange)
        val location: TextView = view.findViewById(R.id.location)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            scheduleItems[position].isFinalExam -> VIEW_TYPE_FINAL
            scheduleItems[position].isEvent -> VIEW_TYPE_EVENT
            else -> VIEW_TYPE_CLASS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_FINAL -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_final_exam, parent, false)
                FinalExamViewHolder(view)
            }
            VIEW_TYPE_EVENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_event, parent, false) // Assuming item_event.xml exists
                EventViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_schedule, parent, false)
                ScheduleViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = scheduleItems[position]
        when (holder) {
            is FinalExamViewHolder -> {
                holder.title.text = item.title
                holder.subtitle.text = item.subtitle
                holder.timeRange.text = "${item.startTime} - ${item.endTime}"
                holder.location.text = "${item.building} ${item.room}"
            }
            is ScheduleViewHolder -> {
                holder.title.text = item.title
                holder.subtitle.text = item.subtitle
                holder.timeRange.text = "${item.startTime} - ${item.endTime}"
                holder.location.text = "${item.building} ${item.room}"
            }
            is EventViewHolder -> {
                holder.title.text = item.title
                holder.subtitle.text = item.subtitle
                holder.timeRange.text = "${item.startTime} - ${item.endTime}"
                holder.location.text = "${item.building} ${item.room}"
            }
        }
    }

    override fun getItemCount() = scheduleItems.size

    fun updateSchedule(newItems: List<ScheduleItem>) {
        scheduleItems.clear()
        scheduleItems.addAll(newItems)
        notifyDataSetChanged()
    }
}
