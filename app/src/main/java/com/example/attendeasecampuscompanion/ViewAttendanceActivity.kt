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
import com.google.firebase.firestore.Query

class ViewAttendanceActivity : AppCompatActivity() {

    private lateinit var courseSpinner: Spinner
    private lateinit var recyclerView: RecyclerView
    private lateinit var generateReportButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val courses = mutableListOf<Course>()
    private val records = mutableListOf<AttendanceRecord>()
    private lateinit var adapter: AttendanceRecordAdapter
    private var selectedCourse: Course? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_attendance)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Attendance Records"

        courseSpinner = findViewById(R.id.courseSpinner)
        recyclerView = findViewById(R.id.recordsRecyclerView)
        generateReportButton = findViewById(R.id.generateReportButton)
        progressBar = findViewById(R.id.progressBar)
        emptyText = findViewById(R.id.emptyText)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AttendanceRecordAdapter(records) { record ->
            showRecordDetails(record)
        }
        recyclerView.adapter = adapter

        loadCourses()

        generateReportButton.setOnClickListener { generateReport() }
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
                loadAttendanceRecords()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadAttendanceRecords() {
        val course = selectedCourse ?: return
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        db.collection("AttendanceRecords")
            .whereEqualTo("courseId", course.courseId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                records.clear()
                for (doc in documents) {
                    records.add(doc.toObject(AttendanceRecord::class.java))
                }
                adapter.notifyDataSetChanged()

                if (records.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                }

                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading records: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun showRecordDetails(record: AttendanceRecord) {
        val present = record.attendanceList.count { it.status == AttendanceStatus.PRESENT }
        val absent = record.attendanceList.count { it.status == AttendanceStatus.ABSENT }
        val late = record.attendanceList.count { it.status == AttendanceStatus.LATE }
        val total = record.attendanceList.size

        val message = buildString {
            append("Date: ${record.date}\n")
            append("Course: ${record.courseCode}\n\n")
            append("Summary:\n")
            append("Present: $present\n")
            append("Absent: $absent\n")
            append("Late: $late\n")
            append("Total: $total\n\n")
            append("Attendance Rate: ${(present * 100 / total)}%")
        }

        AlertDialog.Builder(this)
            .setTitle("Attendance Details")
            .setMessage(message)
            .setPositiveButton("View Students") { _, _ ->
                showStudentList(record)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showStudentList(record: AttendanceRecord) {
        val studentList = record.attendanceList
            .sortedBy { it.studentName }
            .joinToString("\n") { student ->
                val status = when (student.status) {
                    AttendanceStatus.PRESENT -> "✓ Present"
                    AttendanceStatus.ABSENT -> "✗ Absent"
                    AttendanceStatus.LATE -> "⚠ Late"
                    else -> "? Unknown"
                }
                "${student.studentName} - $status"
            }

        AlertDialog.Builder(this)
            .setTitle("Student Attendance - ${record.date}")
            .setMessage(studentList)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun generateReport() {
        val course = selectedCourse
        if (course == null) {
            Toast.makeText(this, "Please select a course", Toast.LENGTH_SHORT).show()
            return
        }

        if (records.isEmpty()) {
            Toast.makeText(this, "No attendance records to generate report", Toast.LENGTH_SHORT).show()
            return
        }

        val report = buildString {
            append("ATTENDANCE REPORT\n")
            append("=================\n\n")
            append("Course: ${course.courseCode} - ${course.courseName}\n")
            append("Professor: ${course.professorName}\n")
            append("Total Records: ${records.size}\n\n")

            append("Overall Statistics:\n")
            val totalPresent = records.sumOf { it.attendanceList.count { s -> s.status == AttendanceStatus.PRESENT } }
            val totalAbsent = records.sumOf { it.attendanceList.count { s -> s.status == AttendanceStatus.ABSENT } }
            val totalLate = records.sumOf { it.attendanceList.count { s -> s.status == AttendanceStatus.LATE } }
            val grandTotal = totalPresent + totalAbsent + totalLate

            append("Total Present: $totalPresent\n")
            append("Total Absent: $totalAbsent\n")
            append("Total Late: $totalLate\n")
            append("Overall Attendance Rate: ${if (grandTotal > 0) (totalPresent * 100 / grandTotal) else 0}%\n\n")

            append("Record Details:\n")
            records.forEach { record ->
                val present = record.attendanceList.count { it.status == AttendanceStatus.PRESENT }
                val total = record.attendanceList.size
                append("${record.date}: $present/$total (${if (total > 0) (present * 100 / total) else 0}%)\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Attendance Report")
            .setMessage(report)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}