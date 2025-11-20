package com.example.attendeasecampuscompanion.map

import com.google.android.gms.maps.model.LatLng

/**
 * TEAM NOTE:
 * Represents one class meeting inside a building.
 * We keep this separate so a single building can have multiple
 * classes for the same user (different rooms/times).
 */
data class CourseMeetingInfo(
    val courseName: String,
    val room: String,
    val startTime: String,
    val endTime: String
)

/**
 * TEAM NOTE:
 * Represents a building that has at least one of the user's classes.
 * `classes` is a list because the user could have multiple courses
 * in the same building.
 */
data class ClassBuildingLocation(
    val building: String,
    val position: LatLng,
    val classes: List<CourseMeetingInfo>
)
