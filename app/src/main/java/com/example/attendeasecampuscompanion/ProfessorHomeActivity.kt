package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ProfessorHomeActivity : AppCompatActivity() {

    private lateinit var professorNameText: TextView
    private lateinit var professorIdText: TextView
    private lateinit var profDateHeader: TextView
    private lateinit var tvCoursesList: TextView
    private lateinit var btnManualAttendance: Button
    private lateinit var btnViewAttendance: Button
    private lateinit var btnViewCourses: Button
    private lateinit var btnMySchedule: Button
    private lateinit var btnSendAnnouncement: Button
    private lateinit var btnProfSignOut: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUser: User? = null
    private var coursesMap: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_professor_home)

        professorNameText = findViewById(R.id.professorName)
        professorIdText = findViewById(R.id.professorId)
        profDateHeader = findViewById(R.id.profDateHeader)
        tvCoursesList = findViewById(R.id.tvCoursesList)
        btnManualAttendance = findViewById(R.id.btnManualAttendance)
        btnViewAttendance = findViewById(R.id.btnViewAttendance)
        btnViewCourses = findViewById(R.id.btnViewCourses)
        btnMySchedule = findViewById(R.id.btnMySchedule)
        btnSendAnnouncement = findViewById(R.id.btnSendAnnouncement)
        btnProfSignOut = findViewById(R.id.btnProfSignOut)

        setCurrentDate()
        loadUserData()
        loadCourses()

        btnManualAttendance.setOnClickListener {
            startActivity(Intent(this, MarkAttendanceActivity::class.java))
        }

        btnViewAttendance.setOnClickListener {
            startActivity(Intent(this, ViewAttendanceActivity::class.java))
        }

        btnViewCourses.setOnClickListener {
            startActivity(Intent(this, ViewCoursesActivity::class.java))
        }

        btnMySchedule.setOnClickListener {
            startActivity(Intent(this, MyScheduleActivity::class.java))
        }

        btnSendAnnouncement.setOnClickListener {
            showAnnouncementDialog()
        }

        btnProfSignOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun setCurrentDate() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.US)
        profDateHeader.text = dateFormat.format(calendar.time)
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).get()
            .addOnSuccessListener { document ->
                currentUser = document.toObject(User::class.java)
                val fullName = "${currentUser?.firstName} ${currentUser?.lastName}"
                professorNameText.text = fullName

                val displayId = currentUser?.userId ?: userId.substring(0, 8)
                professorIdText.text = "ID: $displayId"
            }
            .addOnFailureListener {
                professorNameText.text = "Professor Name"
                professorIdText.text = "ID: N/A"
            }
    }

    private fun loadCourses() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Courses")
            .whereEqualTo("professorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val tempMap = mutableMapOf<String, String>()
                for (doc in documents) {
                    val courseId = doc.getString("courseId") ?: continue
                    val courseName = doc.getString("courseName") ?: continue
                    tempMap[doc.id] = "$courseId - $courseName"
                }
                coursesMap = tempMap

                val courseCount = coursesMap.size
                tvCoursesList.text = "Teaching $courseCount course(s)"
            }
            .addOnFailureListener {
                tvCoursesList.text = "Teaching 0 course(s)"
            }
    }

    private fun showAnnouncementDialog() {
        if (coursesMap.isEmpty()) {
            Toast.makeText(this, "Loading courses...", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_announcement, null)
        val courseSpinner = dialogView.findViewById<Spinner>(R.id.spinnerCourse)
        val messageInput = dialogView.findViewById<EditText>(R.id.etMessage)

        val courseList = coursesMap.values.toList()
        val courseDocIds = coursesMap.keys.toList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courseList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        courseSpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Post") { _, _ ->
                val message = messageInput.text.toString()
                val selectedPosition = courseSpinner.selectedItemPosition

                if (message.isNotBlank() && selectedPosition >= 0) {
                    val courseDocId = courseDocIds[selectedPosition]
                    saveAnnouncement(courseDocId, message)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveAnnouncement(courseDocId: String, message: String) {
        val userId = auth.currentUser?.uid ?: return
        val professorName = "${currentUser?.firstName} ${currentUser?.lastName}"

        db.collection("Courses").document(courseDocId).get()
            .addOnSuccessListener { doc ->
                val courseId = doc.getString("courseId") ?: ""
                val courseName = doc.getString("courseName") ?: ""

                val announcement = hashMapOf(
                    "message" to message,
                    "courseId" to courseId,
                    "courseName" to courseName,
                    "createdBy" to userId,
                    "createdByName" to professorName,
                    "timestamp" to System.currentTimeMillis(),
                    "priority" to "NORMAL"
                )

                db.collection("Courses")
                    .document(courseDocId)
                    .collection("Announcements")
                    .add(announcement)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "Announcement posted to $courseId",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Failed to post announcement: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
    }
}