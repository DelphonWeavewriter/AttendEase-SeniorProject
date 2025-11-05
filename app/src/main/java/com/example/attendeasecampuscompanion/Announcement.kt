package com.example.attendeasecampuscompanion

data class Announcement(
    val announcementId: String = "",
    val title: String = "",
    val message: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val timestamp: Long = 0L,
    val priority: AnnouncementPriority = AnnouncementPriority.NORMAL
) {
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
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

enum class AnnouncementPriority {
    NORMAL,
    URGENT
}