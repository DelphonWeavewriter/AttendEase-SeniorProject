package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MarkAttendanceActivity : AppCompatActivity() {

    private lateinit var courseSpinner: Spinner
    private lateinit var dateText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var submitButton: Button
    private lateinit var progressBar: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val courses = mutableListOf<Course>()
    private val attendanceList = mutableListOf<StudentAttendance>()
    private lateinit var adapter: `AttendanceAdapter.kt`
    private var selectedCourse: Course? = null
    private val selectedDate = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mark_attendance)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mark Attendance"

        courseSpinner = findViewById(R.id.courseSpinner)
        dateText = findViewById(R.id.dateText)
        recyclerView = findViewById(R.id.attendanceRecyclerView)
        submitButton = findViewById(R.id.submitAttendanceButton)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = `AttendanceAdapter.kt`(attendanceList)
        recyclerView.adapter = adapter

        updateDateText()
        loadCourses()

        dateText.setOnClickListener { showDatePicker() }
        submitButton.setOnClickListener { submitAttendance() }
    }

    private fun loadCourses() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("Courses")
            .whereEqualTo("professorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                courses.clear()
                for (doc in documents) {
                    courses.add(doc.toObject(Course::class.java))
                }

                setupCourseSpinner()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading courses: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun setupCourseSpinner() {
        val courseNames = courses.map { "${it.courseCode} - ${it.courseName}" }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courseNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        courseSpinner.adapter = spinnerAdapter

        courseSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCourse = courses[position]
                loadStudentsForCourse()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadStudentsForCourse() {
        val course = selectedCourse ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("Enrollments")
            .whereEqualTo("courseId", course.courseId)
            .whereEqualTo("status", "ACTIVE")
            .get()
            .addOnSuccessListener { documents ->
                attendanceList.clear()

                if (documents.isEmpty) {
                    Toast.makeText(this, "No students enrolled", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    val enrollment = doc.toObject(Enrollment::class.java)
                    attendanceList.add(StudentAttendance(
                        studentId = enrollment.studentId,
                        studentName = enrollment.studentName,
                        status = AttendanceStatus.ABSENT
                    ))
                }
                attendanceList.sortBy { it.studentName }
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun showDatePicker() {
        val datePicker = android.app.DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate.set(year, month, day)
                updateDateText()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun updateDateText() {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dateText.text = sdf.format(selectedDate.time)
    }

    private fun submitAttendance() {
        val course = selectedCourse
        if (course == null) {
            Toast.makeText(this, "Please select a course", Toast.LENGTH_SHORT).show()
            return
        }

        if (attendanceList.isEmpty()) {
            Toast.makeText(this, "No students to mark attendance for", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Submit Attendance")
            .setMessage("Submit attendance for ${course.courseCode} on ${dateText.text}?")
            .setPositiveButton("Submit") { _, _ ->
                saveAttendance(course)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveAttendance(course: Course) {
        progressBar.visibility = View.VISIBLE
        submitButton.isEnabled = false

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val record = AttendanceRecord(
            recordId = UUID.randomUUID().toString(),
            courseId = course.courseId,
            courseCode = course.courseCode,
            date = dateFormat.format(selectedDate.time),
            timestamp = System.currentTimeMillis(),
            professorId = auth.currentUser?.uid ?: "",
            attendanceList = attendanceList.map { it.copy(markedAt = System.currentTimeMillis()) }
        )

        db.collection("AttendanceRecords")
            .document(record.recordId)
            .set(record)
            .addOnSuccessListener {
                Toast.makeText(this, "Attendance saved successfully", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                submitButton.isEnabled = true
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving attendance: ${e.message}", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
                submitButton.isEnabled = true
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}