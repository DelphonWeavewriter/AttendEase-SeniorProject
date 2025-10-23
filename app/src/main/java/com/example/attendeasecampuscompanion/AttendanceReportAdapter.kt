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

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvAttendanceRate: TextView = view.findViewById(R.id.tvAttendanceRate)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvStatusEmoji: TextView = view.findViewById(R.id.tvStatusEmoji)
        val tvCounts: TextView = view.findViewById(R.id.tvCounts)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]

        holder.tvStudentName.text = student.studentName
        holder.tvAttendanceRate.text = String.format("%.1f%%", student.attendanceRate)
        holder.tvStatusEmoji.text = student.getStatusEmoji()

        val statusText = when {
            student.attendanceRate >= 90f -> "Excellent"
            student.attendanceRate >= 75f -> "Good"
            else -> "Needs Attention"
        }
        holder.tvStatus.text = statusText
        holder.tvStatus.setTextColor(student.getStatusColor())

        holder.tvCounts.text = "🟢 ${student.presentCount}  🟡 ${student.lateCount}  🔴 ${student.absentCount}"

        holder.itemView.setOnClickListener {
            onStudentClick(student)
        }
    }

    override fun getItemCount() = students.size
}