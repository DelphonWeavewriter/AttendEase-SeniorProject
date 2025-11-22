package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val user = auth.currentUser

        user?.uid?.let { uid ->
            fetchUserData(uid)
            loadNextClass(uid)
        }

        findViewById<Button>(R.id.btnCheckIn).setOnClickListener {
            Toast.makeText(this, "Starting NFC Scanning...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AttendanceActivity::class.java))
        }

        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, MyScheduleActivity::class.java))
        }

        findViewById<Button>(R.id.btnSocial).setOnClickListener {
            Toast.makeText(this, "Opening SocialEaze...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnCampusMap).setOnClickListener {
            Toast.makeText(this, "Opening your campus map...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, com.example.attendeasecampuscompanion.map.MapActivity::class.java))
        }

        findViewById<Button>(R.id.btnFinalsSchedule).setOnClickListener {
            Toast.makeText(this, "Opening your courses...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ViewCoursesActivity::class.java))
        }

        findViewById<Button>(R.id.btnSignOut).setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun fetchUserData(userId: String) {
        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userData = document.toObject(User::class.java)

                    userData?.let {
                        findViewById<TextView>(R.id.studentName).text = "${it.firstName} ${it.lastName}"
                        findViewById<TextView>(R.id.studentId).text = "ID: ${it.userId}"

                        val dateHeader = findViewById<TextView>(R.id.dateHeader)
                        val formatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                        val currentDate = formatter.format(Date())
                        dateHeader.text = currentDate
                    }
                }
                else {
                    findViewById<TextView>(R.id.studentName).text = auth.currentUser?.email?.substringBefore("@") ?: "Student"
                    findViewById<TextView>(R.id.studentId).text = "ID: Not found"
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Error fetching user data", e)
                Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
                findViewById<TextView>(R.id.studentName).text = auth.currentUser?.email?.substringBefore("@") ?: "Student"
            }
    }

    private fun loadNextClass(userId: String) {
        val currentTime = Calendar.getInstance()
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(currentTime.time)

        db.collection("Courses")
            .whereArrayContains("enrolledStudents", userId)
            .get()
            .addOnSuccessListener { documents ->
                val todaysClasses = mutableListOf<ClassInfo>()

                for (document in documents) {
                    val course = document.toObject(Course::class.java)

                    for (scheduleMap in course.schedule) {
                        if (scheduleMap["dayOfWeek"]?.equals(dayOfWeek, ignoreCase = true) == true) {
                            val startTime = scheduleMap["startTime"] ?: ""
                            val building = scheduleMap["building"] ?: ""
                            val room = scheduleMap["room"] ?: ""

                            todaysClasses.add(
                                ClassInfo(
                                    courseName = course.courseName,
                                    courseId = course.courseId ?: "",
                                    startTime = startTime,
                                    building = building,
                                    room = room
                                )
                            )
                        }
                    }
                }

                if (todaysClasses.isEmpty()) {
                    findViewById<TextView>(R.id.nextClass).text = "No classes scheduled today!"
                } else {
                    todaysClasses.sortBy { convertToMinutes(it.startTime) }

                    val currentMinutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)

                    val nextClass = todaysClasses.firstOrNull {
                        convertToMinutes(it.startTime) > currentMinutes
                    }

                    if (nextClass != null) {
                        findViewById<TextView>(R.id.nextClass).text =
                            "Next Class: ${nextClass.courseName} at ${nextClass.startTime}"
                    } else {
                        findViewById<TextView>(R.id.nextClass).text = "No more classes today!"
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("HomeActivity", "Error loading classes", e)
                findViewById<TextView>(R.id.nextClass).text = "Unable to load schedule"
            }
    }

    private fun convertToMinutes(time: String): Int {
        return try {
            val parts = time.trim().split(":")
            if (parts.size != 2) return 0

            val hour = parts[0].toIntOrNull() ?: 0
            val minutePart = parts[1].trim()

            val minute = minutePart.take(2).toIntOrNull() ?: 0
            val isPM = minutePart.contains("PM", ignoreCase = true)
            val isAM = minutePart.contains("AM", ignoreCase = true)

            var totalHour = hour
            if (isPM && hour != 12) {
                totalHour += 12
            } else if (isAM && hour == 12) {
                totalHour = 0
            }

            totalHour * 60 + minute
        } catch (e: Exception) {
            0
        }
    }

    data class ClassInfo(
        val courseName: String,
        val courseId: String,
        val startTime: String,
        val building: String,
        val room: String
    )
}