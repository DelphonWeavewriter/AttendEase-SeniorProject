package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttendanceReportAdapter(
    private val students: List<StudentAttendanceSummary>,
    private val onStudentClick: (StudentAttendanceSummary) -> Unit
) : RecyclerView.Adapter<AttendanceReportAdapter.ViewHolder>() {

    private val colors = listOf("#42A5F5", "#66BB6A", "#FFA726", "#EF5350", "#AB47BC")

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitials: TextView = view.findViewById(R.id.tvInitials)
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvStatusEmoji: TextView = view.findViewById(R.id.tvStatusEmoji)
        val tvAttendanceRate: TextView = view.findViewById(R.id.tvAttendanceRate)
        val tvPresentCount: TextView = view.findViewById(R.id.tvPresentCount)
        val tvLateCount: TextView = view.findViewById(R.id.tvLateCount)
        val tvAbsentCount: TextView = view.findViewById(R.id.tvAbsentCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]

        val initials = student.studentName.split(" ")
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .take(2)
            .joinToString("")
        holder.tvInitials.text = initials

        val colorIndex = position % colors.size
        val color = android.graphics.Color.parseColor(colors[colorIndex])
        holder.tvInitials.backgroundTintList = android.content.res.ColorStateList.valueOf(color)

        holder.tvStudentName.text = student.studentName

        val rate = student.attendanceRate
        holder.tvAttendanceRate.text = String.format("%.1f%%", rate)
        holder.tvAttendanceRate.setTextColor(student.getStatusColor())

        val (statusText, statusEmoji, statusBg) = when {
            rate >= 90f -> Triple("Excellent", "🟢", "#E8F5E9")
            rate >= 75f -> Triple("Good", "🟡", "#FFF3E0")
            rate >= 60f -> Triple("Needs Attention", "🟠", "#FFE0B2")
            else -> Triple("Critical", "🔴", "#FFEBEE")
        }

        holder.tvStatus.text = statusText
        holder.tvStatus.setBackgroundColor(android.graphics.Color.parseColor(statusBg))
        holder.tvStatusEmoji.text = statusEmoji

        holder.tvPresentCount.text = student.presentCount.toString()
        holder.tvLateCount.text = student.lateCount.toString()
        holder.tvAbsentCount.text = student.absentCount.toString()

        holder.itemView.setOnClickListener {
            onStudentClick(student)
        }
    }

    override fun getItemCount() = students.size
}