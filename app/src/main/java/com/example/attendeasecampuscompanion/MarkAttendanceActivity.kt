package com.example.attendeasecampuscompanion

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MarkAttendanceActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var btnBack: TextView
    private lateinit var txtSubtitle: TextView
    private lateinit var txtSelectedDate: TextView
    private lateinit var recyclerViewCourses: RecyclerView
    private lateinit var recyclerViewStudents: RecyclerView
    private lateinit var rosterSection: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSelectDate: MaterialButton
    private lateinit var btnSaveAttendance: MaterialButton

    private val courses = mutableListOf<Course>()
    private val courseDocIds = mutableListOf<String>()
    private val students = mutableListOf<StudentAttendanceItem>()
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
        btnBack = findViewById(R.id.btnBack)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        txtSelectedDate = findViewById(R.id.txtSelectedDate)
        recyclerViewCourses = findViewById(R.id.recyclerViewCourses)
        recyclerViewStudents = findViewById(R.id.recyclerViewStudents)
        rosterSection = findViewById(R.id.rosterSection)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSaveAttendance = findViewById(R.id.btnSaveAttendance)

        btnBack.setOnClickListener { finish() }
        btnSelectDate.setOnClickListener { showDatePicker() }
        btnSelectDate.isEnabled = false
        btnSaveAttendance.setOnClickListener { saveAttendance() }

        rosterSection.visibility = View.GONE
        emptyState.visibility = View.GONE
    }

    private fun setupRecyclerViews() {
        recyclerViewCourses.layoutManager = LinearLayoutManager(this)
        recyclerViewStudents.layoutManager = LinearLayoutManager(this)
    }

    private fun loadProfessorCourses() {
        val currentUser = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE
        recyclerViewCourses.visibility = View.GONE

        db.collection("Courses")
            .whereEqualTo("professorId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                courses.clear()
                courseDocIds.clear()

                if (documents.isEmpty) {
                    progressBar.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    val course = document.toObject(Course::class.java)
                    courses.add(course)
                    courseDocIds.add(document.id)
                }
                setupCourseAdapter()
                progressBar.visibility = View.GONE
                recyclerViewCourses.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading courses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupCourseAdapter() {
        val adapter = CourseAdapter(courses) { course ->
            val index = courses.indexOf(course)
            val docId = courseDocIds[index]
            onCourseSelected(course, docId)
        }
        recyclerViewCourses.adapter = adapter
    }

    private fun onCourseSelected(course: Course, docId: String) {
        selectedCourse = course
        selectedCourseDocId = docId

        txtSubtitle.text = "Selected: ${course.courseId} - ${course.courseName}"

        btnSelectDate.isEnabled = true
        recyclerViewCourses.visibility = View.GONE

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

            val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val displayDate = displayFormat.format(calendar.time)
            txtSelectedDate.text = displayDate

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
        recyclerViewStudents.adapter = attendanceAdapter
        rosterSection.visibility = View.VISIBLE
        btnSelectDate.visibility = View.INVISIBLE
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
                Toast.makeText(this, "Attendance saved successfully! 🎉", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSaveAttendance.isEnabled = true
                Toast.makeText(this, "Error saving attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}