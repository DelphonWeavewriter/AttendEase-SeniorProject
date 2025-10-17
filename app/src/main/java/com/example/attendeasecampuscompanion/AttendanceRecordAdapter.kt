package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttendanceRecordAdapter(
    private val records: List<AttendanceRecord>,
    private val onRecordClick: (AttendanceRecord) -> Unit
) : RecyclerView.Adapter<AttendanceRecordAdapter.RecordViewHolder>() {

    class RecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.dateText)
        val statsText: TextView = view.findViewById(R.id.statsText)
        val rateText: TextView = view.findViewById(R.id.rateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]

        holder.dateText.text = record.date

        val present = record.attendanceList.count { it.status == AttendanceStatus.PRESENT }
        val total = record.attendanceList.size
        holder.statsText.text = "Present: $present/$total"

        val rate = if (total > 0) (present * 100 / total) else 0
        holder.rateText.text = "$rate%"
        holder.rateText.setTextColor(
            when {
                rate >= 80 -> 0xFF4CAF50.toInt()
                rate >= 60 -> 0xFFFF9800.toInt()
                else -> 0xFFF44336.toInt()
            }
        )

        holder.itemView.setOnClickListener {
            onRecordClick(record)
        }
    }

    override fun getItemCount() = records.size
}