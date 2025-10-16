package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfessorHomeActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_professor_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val user = auth.currentUser

        user?.uid?.let { uid ->
            fetchProfessorData(uid)
        }

        findViewById<Button>(R.id.btnManualAttendance).setOnClickListener {
            Toast.makeText(this, "Opening manual attendance...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnViewAttendance).setOnClickListener {
            Toast.makeText(this, "Opening attendance records...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnViewCourses).setOnClickListener {
            Toast.makeText(this, "Opening your courses...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnMySchedule).setOnClickListener {
            Toast.makeText(this, "Opening your schedule...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnAttendanceReports).setOnClickListener {
            Toast.makeText(this, "Opening attendance reports...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSendAnnouncement).setOnClickListener {
            showAnnouncementDialog()
        }

        findViewById<Button>(R.id.btnProfCampusMap).setOnClickListener {
            Toast.makeText(this, "Opening campus map...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, com.example.attendeasecampuscompanion.map.MapActivity::class.java))
        }

        findViewById<Button>(R.id.btnProfSignOut).setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun fetchProfessorData(userId: String) {
        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userData = document.toObject(User::class.java)

                    userData?.let {
                        findViewById<TextView>(R.id.professorName).text = "Prof. ${it.firstName} ${it.lastName}"
                        findViewById<TextView>(R.id.professorId).text = "ID: ${it.userId}"

                        val dateHeader = findViewById<TextView>(R.id.profDateHeader)
                        val formatter = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault())
                        val currentDate = formatter.format(java.util.Date())
                        dateHeader.text = currentDate

                        loadProfessorCourses(it.enrolledCourses)
                    }
                } else {
                    findViewById<TextView>(R.id.professorName).text = "Prof. ${auth.currentUser?.email?.substringBefore("@") ?: "Professor"}"
                    findViewById<TextView>(R.id.professorId).text = "ID: Not found"
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Error fetching professor data", e)
                Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfessorCourses(courseIds: List<String>) {
        if (courseIds.isEmpty()) {
            findViewById<TextView>(R.id.tvNoCourses).text = "No courses assigned yet"
            return
        }

        val coursesText = findViewById<TextView>(R.id.tvCoursesList)
        coursesText.text = "Teaching ${courseIds.size} course(s)"
    }

    private fun showAnnouncementDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_announcement, null)
        val courseInput = dialogView.findViewById<EditText>(R.id.etCourseName)
        val messageInput = dialogView.findViewById<EditText>(R.id.etMessage)

        AlertDialog.Builder(this)
            .setTitle("Send Announcement")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val course = courseInput.text.toString().trim()
                val message = messageInput.text.toString().trim()

                if (course.isEmpty() || message.isEmpty()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Announcement sent to $course", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
}