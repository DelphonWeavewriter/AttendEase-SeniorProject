package com.example.attendeasecampuscompanion

data class User(
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val studentId: String = "",
    val campus: String = "",
    val department: String = "",
    val major: String = "",
    val phoneNum: String = "",
    val professorId: String = "",
    val role: String = "",
    val enrolledCourses: List<String> = emptyList()
)