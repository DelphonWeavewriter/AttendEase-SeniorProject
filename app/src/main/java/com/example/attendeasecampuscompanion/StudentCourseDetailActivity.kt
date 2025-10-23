package com.example.attendeasecampuscompanion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentCourseDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var courseCodeText: TextView
    private lateinit var courseNameText: TextView
    private lateinit var professorNameText: TextView
    private lateinit var scheduleText: TextView
    private lateinit var locationText: TextView
    private lateinit var semesterText: TextView
    private lateinit var creditsText: TextView
    private lateinit var departmentText: TextView
    private lateinit var enrollmentText: TextView

    private lateinit var btnNavigate: MaterialButton
    private lateinit var btnEmailProfessor: MaterialButton
    private lateinit var btnBack: ImageButton

    private var professorEmail: String? = null
    private var roomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_course_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()

        val courseId = intent.getStringExtra("courseId") ?: ""
        val courseDocId = intent.getStringExtra("courseDocId") ?: ""

        if (courseDocId.isNotEmpty()) {
            loadCourseDetails(courseDocId)
        } else {
            Toast.makeText(this, "Error loading course details", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnNavigate.setOnClickListener {
            roomId?.let { roomID ->
                val intent = Intent(this, com.example.attendeasecampuscompanion.map.MapActivity::class.java)
                intent.putExtra("roomId", roomID)
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, "Room location not available", Toast.LENGTH_SHORT).show()
            }
        }

        btnEmailProfessor.setOnClickListener {
            professorEmail?.let { email ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$email")
                    putExtra(Intent.EXTRA_SUBJECT, "Question about ${courseCodeText.text}")
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "Professor email not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeViews() {
        courseCodeText = findViewById(R.id.courseCode)
        courseNameText = findViewById(R.id.courseName)
        professorNameText = findViewById(R.id.professorName)
        scheduleText = findViewById(R.id.scheduleDetails)
        locationText = findViewById(R.id.locationDetails)
        semesterText = findViewById(R.id.semesterDetails)
        creditsText = findViewById(R.id.creditsDetails)
        departmentText = findViewById(R.id.departmentDetails)
        enrollmentText = findViewById(R.id.enrollmentDetails)

        btnNavigate = findViewById(R.id.btnNavigate)
        btnEmailProfessor = findViewById(R.id.btnEmailProfessor)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun loadCourseDetails(courseDocId: String) {
        db.collection("Courses").document(courseDocId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val course = document.toObject(Course::class.java)
                    course?.let {
                        displayCourseDetails(it)
                        loadProfessorEmail(it.professorId)
                        loadStudentAttendance(courseDocId)
                    }
                } else {
                    Toast.makeText(this, "Course not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading course: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayCourseDetails(course: Course) {
        courseCodeText.text = course.courseId
        courseNameText.text = course.courseName
        professorNameText.text = "👨‍🏫 Professor: ${course.professorName}"

        val scheduleString = buildScheduleString(course.schedule)
        scheduleText.text = scheduleString

        val building = course.schedule.firstOrNull()?.building ?: "N/A"
        val room = course.schedule.firstOrNull()?.room ?: "N/A"
        locationText.text = "📍 $building Room $room"

        roomId = course.roomID

        semesterText.text = "📅 ${course.semester}"
        creditsText.text = "📚 ${course.credits} Credits"
        departmentText.text = "🏫 ${course.department}"

        val enrolled = course.enrolledStudents.size
        val capacity = course.maxCapacity
        enrollmentText.text = "👥 $enrolled / $capacity students"
    }

    private fun buildScheduleString(schedule: List<Course.ClassSchedule>): String {
        if (schedule.isEmpty()) return "🕐 Schedule not available"

        val scheduleItems = schedule.map { classSchedule ->
            "${classSchedule.dayOfWeek} ${classSchedule.startTime} - ${classSchedule.endTime}"
        }

        return "🕐 " + scheduleItems.joinToString("\n     ")
    }

    private fun loadProfessorEmail(professorId: String) {
        db.collection("Users").document(professorId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    professorEmail = document.getString("email")
                }
            }
    }

    private fun loadStudentAttendance(courseDocId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Courses").document(courseDocId)
            .collection("AttendanceRecords")
            .whereEqualTo("studentId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                val totalClasses = documents.size()
                if (totalClasses > 0) {
                    val presentCount = documents.count { it.getString("status") == "PRESENT" }
                    val attendanceRate = (presentCount.toDouble() / totalClasses * 100).toInt()

                    val attendanceText = findViewById<TextView>(R.id.attendanceRate)
                    attendanceText.text = "✅ Your Attendance: $attendanceRate% ($presentCount/$totalClasses classes)"

                    attendanceText.setTextColor(
                        when {
                            attendanceRate >= 90 -> getColor(R.color.success_green)
                            attendanceRate >= 75 -> getColor(R.color.warning_yellow)
                            else -> getColor(R.color.error_red)
                        }
                    )
                }
            }
    }
}