package com.example.attendeasecampuscompanion

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class CourseAdapter(
    private val courses: List<Course>,
    private val onCourseClick: (Course) -> Unit
) : RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    class CourseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val leftStripe: View = view.findViewById(R.id.leftStripe)
        val txtCourseId: TextView = view.findViewById(R.id.txtCourseId)
        val txtCourseName: TextView = view.findViewById(R.id.txtCourseName)
        val txtEnrollment: TextView = view.findViewById(R.id.txtEnrollment)
        val chipSemester: TextView = view.findViewById(R.id.chipSemester)
        val chipSchedule: TextView = view.findViewById(R.id.chipSchedule)
        val txtLocation: TextView = view.findViewById(R.id.txtLocation)
        val cardView: MaterialCardView = view as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courses[position]

        holder.txtCourseId.text = course.courseId
        holder.txtCourseName.text = course.courseName

        val enrolledCount = course.enrolledStudents.size
        val maxCapacity = course.maxCapacity
        holder.txtEnrollment.text = "$enrolledCount/$maxCapacity"

        val enrollmentPercent = if (maxCapacity > 0) {
            (enrolledCount.toFloat() / maxCapacity.toFloat() * 100).toInt()
        } else 0

        holder.txtEnrollment.setTextColor(
            when {
                enrollmentPercent >= 90 -> Color.parseColor("#EF5350")
                enrollmentPercent >= 75 -> Color.parseColor("#FFA726")
                else -> Color.parseColor("#66BB6A")
            }
        )

        holder.chipSemester.text = course.semester

        if (course.schedule.isNotEmpty()) {
            val firstSchedule = course.schedule[0]
            val dayOfWeek = firstSchedule["dayOfWeek"] as? String ?: ""
            val startTime = firstSchedule["startTime"] as? String ?: ""
            val dayAbbrev = dayOfWeek.take(3).uppercase()
            holder.chipSchedule.text = "$dayAbbrev $startTime"
            holder.chipSchedule.visibility = View.VISIBLE
        } else {
            holder.chipSchedule.visibility = View.GONE
        }

        if (course.schedule.isNotEmpty()) {
            val firstSchedule = course.schedule[0]
            val building = firstSchedule["building"] as? String ?: ""
            val room = firstSchedule["room"] as? String ?: ""
            holder.txtLocation.text = "$building $room".trim()
        } else {
            holder.txtLocation.text = course.roomID ?: ""
        }

        val colors = listOf(
            "#42A5F5",
            "#66BB6A",
            "#FFA726",
            "#AB47BC",
            "#26C6DA"
        )
        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition != RecyclerView.NO_POSITION) {
            holder.leftStripe.setBackgroundColor(Color.parseColor(colors[adapterPosition % colors.size]))
        }

        holder.cardView.setOnClickListener {
            onCourseClick(course)
        }
    }

    override fun getItemCount() = courses.size
}