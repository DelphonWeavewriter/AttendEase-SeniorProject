package com.example.attendeasecampuscompanion

data class AttendanceRecord(
    val recordId: String = "",
    val courseId: String = "",
    val courseCode: String = "",
    val date: String = "",
    val timestamp: Long = 0L,
    val professorId: String = "",
    val attendanceList: List<StudentAttendance> = emptyList()
)

data class StudentAttendance(
    val studentId: String = "",
    val studentName: String = "",
    val status: AttendanceStatus = AttendanceStatus.ABSENT,
    val markedAt: Long = 0L,
    val notes: String = ""
)

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    EXCUSED
}