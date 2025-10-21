package com.example.attendeasecampuscompanion

data class AttendanceRecord(
    val recordId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val courseId: String = "",
    val date: String = "",
    val timestamp: Long = 0L,
    val status: String = "PRESENT",
    val method: String = "MANUAL",
    val notes: String = "",
    val lastModified: Long = 0L
)

enum class AttendanceStatus(val value: String) {
    PRESENT("PRESENT"),
    LATE("LATE"),
    ABSENT("ABSENT"),
    EXCUSED("EXCUSED");

    companion object {
        fun fromString(value: String): AttendanceStatus {
            return values().find { it.value == value } ?: PRESENT
        }
    }
}

enum class AttendanceMethod(val value: String) {
    MANUAL("MANUAL"),
    NFC("NFC");

    companion object {
        fun fromString(value: String): AttendanceMethod {
            return values().find { it.value == value } ?: MANUAL
        }
    }
}

data class AttendanceSession(
    val courseId: String = "",
    val courseName: String = "",
    val courseCode: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val isActive: Boolean = false,
    val professorId: String = "",
    val expectedStudents: Int = 0,
    val scannedStudents: Int = 0
)

data class StudentAttendanceItem(
    val studentId: String = "",
    val studentName: String = "",
    var status: String = "PRESENT"
)

data class StudentAttendanceSummary(
    val studentId: String,
    val studentName: String,
    val totalClasses: Int,
    val presentCount: Int,
    val lateCount: Int,
    val absentCount: Int,
    val attendanceRate: Float,
    val records: List<AttendanceRecord> = emptyList()
) {
    fun getStatusColor(): Int {
        return when {
            attendanceRate >= 90f -> android.graphics.Color.parseColor("#66BB6A")
            attendanceRate >= 75f -> android.graphics.Color.parseColor("#FFA726")
            else -> android.graphics.Color.parseColor("#EF5350")
        }
    }

    fun getStatusEmoji(): String {
        return when {
            attendanceRate >= 90f -> "🟢"
            attendanceRate >= 75f -> "🟡"
            else -> "🔴"
        }
    }
}

data class CourseAttendanceSummary(
    val courseId: String,
    val courseName: String,
    val totalStudents: Int,
    val presentCount: Int,
    val lateCount: Int,
    val absentCount: Int,
    val attendanceDate: String,
    val studentSummaries: List<StudentAttendanceSummary> = emptyList()
) {
    val overallAttendanceRate: Float
        get() = if (totalStudents > 0) (presentCount.toFloat() / totalStudents * 100) else 0f

    val presentPercentage: Float
        get() = if (totalStudents > 0) (presentCount.toFloat() / totalStudents * 100) else 0f

    val latePercentage: Float
        get() = if (totalStudents > 0) (lateCount.toFloat() / totalStudents * 100) else 0f

    val absentPercentage: Float
        get() = if (totalStudents > 0) (absentCount.toFloat() / totalStudents * 100) else 0f
}