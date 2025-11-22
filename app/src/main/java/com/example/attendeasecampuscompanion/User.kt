package com.example.attendeasecampuscompanion

data class User(
    val firstName: String = "",
    val lastName: String = "",
    val userId: String = "",
    val email: String = "",
    val role: String = "",
    val campus: String = "",
    val department: String = "",
    val major: String = "",
    val phoneNum: String = "",
    val enrolledCourses: List<String> = emptyList(),
    val profilePictureUrl: String = "",
    val bio: String = "",
    val settings: Map<String, Any> = mapOf(
        "notificationsEnabled" to true,
        "locationSharingEnabled" to false,
        "profilePrivacy" to "public"
    ),
    val lastSeen: Long = 0L
)