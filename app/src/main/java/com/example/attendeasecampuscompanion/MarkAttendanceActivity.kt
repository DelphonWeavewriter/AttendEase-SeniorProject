package com.example.attendeasecampuscompanion

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MarkAttendanceActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvTitle: TextView
    private lateinit var tvSelectedCourse: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var rvCourses: RecyclerView
    private lateinit var rvStudents: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSelectDate: Button
    private lateinit var btnSaveAttendance: Button
    private lateinit var fabBack: FloatingActionButton

    private val courses = mutableListOf<Course>()
    private val students = mutableListOf<StudentAttendanceItem>()
    private lateinit var courseAdapter: CourseAdapter
    private lateinit var attendanceAdapter: ManualAttendanceAdapter

    private var selectedCourse: Course? = null
    private var selectedDate: String = ""
    private var selectedCourseDocId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mark_attendance)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        setupRecyclerViews()
        loadProfessorCourses()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvSelectedCourse = findViewById(R.id.tvSelectedCourse)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        rvCourses = findViewById(R.id.rvCourses)
        rvStudents = findViewById(R.id.rvStudents)
        progressBar = findViewById(R.id.progressBar)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSaveAttendance = findViewById(R.id.btnSaveAttendance)
        fabBack = findViewById(R.id.fabBack)

        fabBack.setOnClickListener { finish() }

        btnSelectDate.setOnClickListener { showDatePicker() }
        btnSelectDate.isEnabled = false

        btnSaveAttendance.setOnClickListener { saveAttendance() }
        btnSaveAttendance.visibility = View.GONE
    }

    private fun setupRecyclerViews() {
        rvCourses.layoutManager = LinearLayoutManager(this)
        rvStudents.layoutManager = LinearLayoutManager(this)
        rvStudents.visibility = View.GONE
    }

    private fun loadProfessorCourses() {
        val currentUser = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("Courses")
            .whereEqualTo("professorId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                courses.clear()
                for (document in documents) {
                    val course = document.toObject(Course::class.java)
                    courses.add(course)
                }
                setupCourseAdapter()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading courses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupCourseAdapter() {
        courseAdapter = CourseAdapter(courses) { course, docId ->
            onCourseSelected(course, docId)
        }
        rvCourses.adapter = courseAdapter
    }

    private fun onCourseSelected(course: Course, docId: String) {
        selectedCourse = course
        selectedCourseDocId = docId

        tvSelectedCourse.text = "Selected: ${course.courseId} - ${course.courseName}"
        tvSelectedCourse.visibility = View.VISIBLE

        btnSelectDate.isEnabled = true
        rvCourses.visibility = View.GONE

        Toast.makeText(this, "Now select a date", Toast.LENGTH_SHORT).show()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            selectedDate = dateFormat.format(calendar.time)

            tvSelectedDate.text = "Date: $selectedDate"
            tvSelectedDate.visibility = View.VISIBLE

            loadEnrolledStudents()
        }, year, month, day).show()
    }

    private fun loadEnrolledStudents() {
        val course = selectedCourse ?: return
        progressBar.visibility = View.VISIBLE
        students.clear()

        if (course.enrolledStudents.isEmpty()) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "No students enrolled in this course", Toast.LENGTH_SHORT).show()
            return
        }

        val loadedStudents = mutableListOf<StudentAttendanceItem>()
        var loadedCount = 0

        for (studentId in course.enrolledStudents) {
            db.collection("Users")
                .whereEqualTo(com.google.firebase.firestore.FieldPath.documentId(), studentId)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val userDoc = documents.documents[0]
                        val firstName = userDoc.getString("firstName") ?: ""
                        val lastName = userDoc.getString("lastName") ?: ""
                        val fullName = "$firstName $lastName"

                        loadedStudents.add(
                            StudentAttendanceItem(
                                studentId = studentId,
                                studentName = fullName,
                                status = "PRESENT"
                            )
                        )
                    }

                    loadedCount++
                    if (loadedCount == course.enrolledStudents.size) {
                        students.addAll(loadedStudents.sortedBy { it.studentName })
                        setupAttendanceAdapter()
                        progressBar.visibility = View.GONE
                    }
                }
                .addOnFailureListener {
                    loadedCount++
                    if (loadedCount == course.enrolledStudents.size) {
                        students.addAll(loadedStudents.sortedBy { it.studentName })
                        setupAttendanceAdapter()
                        progressBar.visibility = View.GONE
                    }
                }
        }
    }

    private fun setupAttendanceAdapter() {
        attendanceAdapter = ManualAttendanceAdapter(students)
        rvStudents.adapter = attendanceAdapter
        rvStudents.visibility = View.VISIBLE
        btnSaveAttendance.visibility = View.VISIBLE
        btnSelectDate.visibility = View.GONE
    }

    private fun saveAttendance() {
        val course = selectedCourse ?: return
        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSaveAttendance.isEnabled = false

        val attendanceData = attendanceAdapter.getAttendanceData()
        val batch = db.batch()
        val timestamp = System.currentTimeMillis()

        for (studentItem in attendanceData) {
            val recordId = "${selectedDate}_${studentItem.studentId}"
            val attendanceRecord = AttendanceRecord(
                recordId = recordId,
                studentId = studentItem.studentId,
                studentName = studentItem.studentName,
                courseId = course.courseId,
                date = selectedDate,
                timestamp = timestamp,
                status = studentItem.status,
                method = "MANUAL",
                notes = "",
                lastModified = timestamp
            )

            val docRef = db.collection("Courses")
                .document(selectedCourseDocId)
                .collection("AttendanceRecords")
                .document(recordId)

            batch.set(docRef, attendanceRecord)
        }

        batch.commit()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Attendance saved successfully!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSaveAttendance.isEnabled = true
                Toast.makeText(this, "Error saving attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}