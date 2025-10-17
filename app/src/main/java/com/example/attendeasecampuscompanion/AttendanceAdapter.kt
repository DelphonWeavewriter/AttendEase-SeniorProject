package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttendanceAdapter(
    private val attendanceList: MutableList<StudentAttendance>
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    class AttendanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val studentName: TextView = view.findViewById(R.id.studentNameText)
        val radioGroup: RadioGroup = view.findViewById(R.id.statusRadioGroup)
        val presentRadio: RadioButton = view.findViewById(R.id.presentRadio)
        val absentRadio: RadioButton = view.findViewById(R.id.absentRadio)
        val lateRadio: RadioButton = view.findViewById(R.id.lateRadio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val attendance = attendanceList[position]
        holder.studentName.text = attendance.studentName

        when (attendance.status) {
            AttendanceStatus.PRESENT -> holder.presentRadio.isChecked = true
            AttendanceStatus.ABSENT -> holder.absentRadio.isChecked = true
            AttendanceStatus.LATE -> holder.lateRadio.isChecked = true
            else -> holder.absentRadio.isChecked = true
        }

        holder.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newStatus = when (checkedId) {
                R.id.presentRadio -> AttendanceStatus.PRESENT
                R.id.lateRadio -> AttendanceStatus.LATE
                else -> AttendanceStatus.ABSENT
            }
            attendanceList[holder.bindingAdapterPosition] = attendance.copy(status = newStatus)
        }
    }

    override fun getItemCount() = attendanceList.size
}