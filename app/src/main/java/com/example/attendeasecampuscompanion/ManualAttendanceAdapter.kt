package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class ManualAttendanceAdapter(
    private val students: MutableList<StudentAttendanceItem>
) : RecyclerView.Adapter<ManualAttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtStudentName: TextView = view.findViewById(R.id.txtStudentName)
        val btnPresent: MaterialButton = view.findViewById(R.id.btnPresent)
        val btnLate: MaterialButton = view.findViewById(R.id.btnLate)
        val btnAbsent: MaterialButton = view.findViewById(R.id.btnAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manual_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.txtStudentName.text = student.studentName

        updateButtonStates(holder, student.status)

        holder.btnPresent.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                students[adapterPosition].status = "PRESENT"
                updateButtonStates(holder, "PRESENT")
            }
        }

        holder.btnLate.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                students[adapterPosition].status = "LATE"
                updateButtonStates(holder, "LATE")
            }
        }

        holder.btnAbsent.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                students[adapterPosition].status = "ABSENT"
                updateButtonStates(holder, "ABSENT")
            }
        }
    }

    private fun updateButtonStates(holder: ViewHolder, status: String) {
        holder.btnPresent.isSelected = (status == "PRESENT")
        holder.btnLate.isSelected = (status == "LATE")
        holder.btnAbsent.isSelected = (status == "ABSENT")

        holder.btnPresent.alpha = if (status == "PRESENT") 1.0f else 0.5f
        holder.btnLate.alpha = if (status == "LATE") 1.0f else 0.5f
        holder.btnAbsent.alpha = if (status == "ABSENT") 1.0f else 0.5f
    }

    override fun getItemCount() = students.size

    fun getAttendanceData(): List<StudentAttendanceItem> = students
}