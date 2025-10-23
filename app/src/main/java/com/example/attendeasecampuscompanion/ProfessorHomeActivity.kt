package com.example.attendeasecampuscompanion

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
    private lateinit var btnCreateEvent: Button
    private lateinit var btnProfSignOut: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUser: User? = null

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
        btnCreateEvent = findViewById(R.id.btnCreateEvent)
        btnProfSignOut = findViewById(R.id.btnProfSignOut)

        setCurrentDate()
        loadUserData()
        loadCourseCount()

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

        btnCreateEvent.setOnClickListener {
            showEventDialog()
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

    private fun loadCourseCount() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Courses")
            .whereEqualTo("professorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val courseCount = documents.size()
                tvCoursesList.text = "Teaching $courseCount course(s)"
            }
            .addOnFailureListener {
                tvCoursesList.text = "Teaching 0 course(s)"
            }
    }

    private fun showAnnouncementDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_announcement, null)
        val courseSpinner = dialogView.findViewById<Spinner>(R.id.spinnerCourse)
        val messageInput = dialogView.findViewById<EditText>(R.id.etMessage)

        val courses = listOf("CSCI 145", "CSCI 426", "CSCI 436")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        courseSpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Create Announcement")
            .setView(dialogView)
            .setPositiveButton("Post") { _, _ ->
                val message = messageInput.text.toString()
                val selectedCourse = courseSpinner.selectedItem?.toString() ?: ""

                if (message.isNotBlank() && selectedCourse.isNotBlank()) {
                    Toast.makeText(this, "Announcement posted for $selectedCourse", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEventDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_event, null)
        val eventNameInput = dialogView.findViewById<EditText>(R.id.etEventName)
        val eventLocationInput = dialogView.findViewById<EditText>(R.id.etEventLocation)
        val eventDateInput = dialogView.findViewById<EditText>(R.id.etEventDate)
        val eventTimeInput = dialogView.findViewById<EditText>(R.id.etEventTime)
        val courseSpinner = dialogView.findViewById<Spinner>(R.id.spinnerEventCourse)

        val courses = listOf("CSCI 145", "CSCI 426", "CSCI 436")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        courseSpinner.adapter = adapter

        val calendar = Calendar.getInstance()

        eventDateInput.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    eventDateInput.setText("${month + 1}/$day/$year")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        eventTimeInput.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    val amPm = if (hour >= 12) "PM" else "AM"
                    val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
                    eventTimeInput.setText(String.format("%02d:%02d %s", displayHour, minute, amPm))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Create Event")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = eventNameInput.text.toString()
                val location = eventLocationInput.text.toString()
                val date = eventDateInput.text.toString()
                val time = eventTimeInput.text.toString()
                val course = courseSpinner.selectedItem?.toString() ?: ""

                if (name.isNotBlank() && date.isNotBlank() && time.isNotBlank()) {
                    Toast.makeText(this, "Event '$name' created for $course on $date at $time", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}