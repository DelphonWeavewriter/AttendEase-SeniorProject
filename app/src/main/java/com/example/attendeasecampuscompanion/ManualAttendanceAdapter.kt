package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ManualAttendanceAdapter(
    private val students: List<StudentAttendanceItem>
) : RecyclerView.Adapter<ManualAttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val studentName: TextView = view.findViewById(R.id.tvStudentName)
        val btnPresent: Button = view.findViewById(R.id.btnPresent)
        val btnLate: Button = view.findViewById(R.id.btnLate)
        val btnAbsent: Button = view.findViewById(R.id.btnAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manual_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.studentName.text = student.studentName

        updateButtonStates(holder, student.status)

        holder.btnPresent.setOnClickListener { view ->
            view.performClick()
            student.status = "PRESENT"
            updateButtonStates(holder, "PRESENT")
        }

        holder.btnLate.setOnClickListener { view ->
            view.performClick()
            student.status = "LATE"
            updateButtonStates(holder, "LATE")
        }

        holder.btnAbsent.setOnClickListener { view ->
            view.performClick()
            student.status = "ABSENT"
            updateButtonStates(holder, "ABSENT")
        }

        holder.itemView.setOnClickListener(null)
    }

    private fun updateButtonStates(holder: ViewHolder, status: String) {
        val context = holder.itemView.context

        holder.btnPresent.isSelected = status == "PRESENT"
        holder.btnLate.isSelected = status == "LATE"
        holder.btnAbsent.isSelected = status == "ABSENT"

        when (status) {
            "PRESENT" -> {
                holder.btnPresent.setBackgroundColor(ContextCompat.getColor(context, R.color.success_green))
                holder.btnLate.setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray))
                holder.btnAbsent.setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray))
            }
            "LATE" -> {
                holder.btnPresent.setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray))
                holder.btnLate.setBackgroundColor(ContextCompat.getColor(context, R.color.warning_yellow))
                holder.btnAbsent.setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray))
            }
            "ABSENT" -> {
                holder.btnPresent.setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray))
                holder.btnLate.setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray))
                holder.btnAbsent.setBackgroundColor(ContextCompat.getColor(context, R.color.error_red))
            }
        }
    }

    override fun getItemCount() = students.size

    fun getAttendanceData(): List<StudentAttendanceItem> = students
}