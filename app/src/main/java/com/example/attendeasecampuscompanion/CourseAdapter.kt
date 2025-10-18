package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CourseAdapter(
    private val courses: List<Course>,
    private val onCourseClick: (Course) -> Unit
) : RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    class CourseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val courseCode: TextView = view.findViewById(R.id.courseCode)
        val courseName: TextView = view.findViewById(R.id.courseName)
        val semester: TextView = view.findViewById(R.id.semester)
        val enrollmentInfo: TextView = view.findViewById(R.id.enrollmentInfo)
        val scheduleInfo: TextView = view.findViewById(R.id.scheduleInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courses[position]

        holder.courseCode.text = course.courseId
        holder.courseName.text = course.courseName
        holder.semester.text = course.semester
        holder.enrollmentInfo.text = "${course.enrolledStudents.size}/${course.maxCapacity} students"

        val scheduleText = if (course.schedule.isNotEmpty()) {
            course.schedule.joinToString("\n") { scheduleItem ->
                "${scheduleItem["dayOfWeek"]} ${scheduleItem["startTime"]}-${scheduleItem["endTime"]}"
            }
        } else {
            "No schedule available"
        }
        holder.scheduleInfo.text = scheduleText

        holder.itemView.setOnClickListener {
            onCourseClick(course)
        }
    }

    override fun getItemCount() = courses.size
}