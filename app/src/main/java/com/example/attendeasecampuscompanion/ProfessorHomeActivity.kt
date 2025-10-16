package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfessorHomeActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var professorCourses: List<String> = emptyList()

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

        findViewById<Button>(R.id.btnCreateEvent).setOnClickListener {
            showCreateEventDialog()
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

                        professorCourses = it.enrolledCourses
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
        if (professorCourses.isEmpty()) {
            Toast.makeText(this, "You have no courses assigned", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_announcement, null)
        val courseSpinner = dialogView.findViewById<Spinner>(R.id.spinnerCourse)
        val messageInput = dialogView.findViewById<EditText>(R.id.etMessage)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, professorCourses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        courseSpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Send Announcement")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val selectedCourse = courseSpinner.selectedItem.toString()
                val message = messageInput.text.toString().trim()

                if (message.isEmpty()) {
                    Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Announcement sent to $selectedCourse", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    private fun showCreateEventDialog() {
        if (professorCourses.isEmpty()) {
            Toast.makeText(this, "You have no courses assigned", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_event, null)
        val eventNameInput = dialogView.findViewById<EditText>(R.id.etEventName)
        val courseSpinner = dialogView.findViewById<Spinner>(R.id.spinnerEventCourse)
        val dateInput = dialogView.findViewById<EditText>(R.id.etEventDate)
        val timeInput = dialogView.findViewById<EditText>(R.id.etEventTime)
        val locationInput = dialogView.findViewById<EditText>(R.id.etEventLocation)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.etEventDescription)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, professorCourses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        courseSpinner.adapter = adapter

        dateInput.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    dateInput.setText(String.format("%02d/%02d/%d", month + 1, day, year))
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        timeInput.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.TimePickerDialog(
                this,
                { _, hour, minute ->
                    val amPm = if (hour >= 12) "PM" else "AM"
                    val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
                    timeInput.setText(String.format("%02d:%02d %s", displayHour, minute, amPm))
                },
                calendar.get(java.util.Calendar.HOUR_OF_DAY),
                calendar.get(java.util.Calendar.MINUTE),
                false
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Create Event")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val eventName = eventNameInput.text.toString().trim()
                val selectedCourse = courseSpinner.selectedItem.toString()
                val date = dateInput.text.toString().trim()
                val time = timeInput.text.toString().trim()

                if (eventName.isEmpty() || date.isEmpty() || time.isEmpty()) {
                    Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Event '$eventName' created for $selectedCourse", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
}