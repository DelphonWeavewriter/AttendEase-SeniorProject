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