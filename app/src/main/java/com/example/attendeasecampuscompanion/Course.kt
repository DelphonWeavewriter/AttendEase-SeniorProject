package com.example.attendeasecampuscompanion

data class Course(
    val courseId: String = "",
    val courseName: String = "",
    val professorId: String = "",
    val professorName: String = "",
    val department: String = "",
    val semester: String = "",
    val campus: String = "",
    val credits: Int = 0,
    val maxCapacity: Int = 0,
    val room: String = "",
    val roomID: String = "",
    val schedule: List<Map<String, String>> = emptyList(),
    val enrolledStudents: List<String> = emptyList()
)