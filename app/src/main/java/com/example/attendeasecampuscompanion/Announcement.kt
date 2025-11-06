package com.example.attendeasecampuscompanion

import java.text.SimpleDateFormat
import java.util.*

data class Announcement(
    val announcementId: String = "",
    val title: String = "",
    val message: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val timestamp: Long = 0L,
    val priority: String = "NORMAL"
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getTimeAgo(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }
}