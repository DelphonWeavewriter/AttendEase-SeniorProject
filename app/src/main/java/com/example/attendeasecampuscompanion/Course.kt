package com.example.attendeasecampuscompanion

data class Course(
    val courseId: String = "",
    val courseCode: String = "",
    val courseName: String = "",
    val professorId: String = "",
    val professorName: String = "",
    val department: String = "",
    val semester: String = "",
    val campus: String = "",
    val schedule: List<ClassSchedule> = emptyList(),
    val maxCapacity: Int = 0,
    val room: String = "",
    val roomID: String = "",
    val credits: Int = 0
)

data class ClassSchedule(
    val dayOfWeek: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val building: String = "",
    val room: String = ""
)