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
    private lateinit var btnViewAnnouncements: MaterialButton
    private lateinit var btnBack: ImageButton

    private var professorEmail: String? = null
    private var roomId: String? = null
    private var courseDocId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_course_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()

        val courseId = intent.getStringExtra("courseId") ?: ""
        courseDocId = intent.getStringExtra("courseDocId") ?: ""

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

        btnViewAnnouncements.setOnClickListener {
            val intent = Intent(this, ViewAnnouncementsActivity::class.java)
            intent.putExtra("courseDocId", courseDocId)
            intent.putExtra("courseName", courseCodeText.text.toString())
            startActivity(intent)
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
        btnViewAnnouncements = findViewById(R.id.btnViewAnnouncements)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun loadCourseDetails(courseDocId: String) {
        db.collection("Courses").document(courseDocId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val courseId = document.getString("courseId") ?: ""
                    val courseName = document.getString("courseName") ?: ""
                    val professorId = document.getString("professorId") ?: ""
                    val professorName = document.getString("professorName") ?: ""
                    val department = document.getString("department") ?: ""
                    val semester = document.getString("semester") ?: ""
                    val credits = document.getLong("credits")?.toInt() ?: 0
                    val maxCapacity = document.getLong("maxCapacity")?.toInt() ?: 0
                    val roomID = document.getString("roomID") ?: ""
                    val enrolledStudents = document.get("enrolledStudents") as? List<String> ?: emptyList()
                    val schedule = document.get("schedule") as? List<Map<String, Any>> ?: emptyList()

                    android.util.Log.d("StudentCourseDetail", "Loaded course: $courseId")
                    android.util.Log.d("StudentCourseDetail", "Professor ID: '$professorId'")
                    android.util.Log.d("StudentCourseDetail", "Professor Name: $professorName")

                    displayCourseDetails(
                        courseId, courseName, professorName, department,
                        semester, credits, maxCapacity, roomID,
                        enrolledStudents, schedule
                    )
                    loadProfessorEmail(professorId)
                    loadStudentAttendance(courseDocId)
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

    private fun displayCourseDetails(
        courseId: String,
        courseName: String,
        professorName: String,
        department: String,
        semester: String,
        credits: Int,
        maxCapacity: Int,
        roomID: String,
        enrolledStudents: List<String>,
        schedule: List<Map<String, Any>>
    ) {
        courseCodeText.text = courseId
        courseNameText.text = courseName
        professorNameText.text = "👨‍🏫 Professor: $professorName"

        val scheduleString = buildScheduleString(schedule)
        scheduleText.text = scheduleString

        val building = (schedule.firstOrNull()?.get("building") as? String) ?: "N/A"
        val room = (schedule.firstOrNull()?.get("room") as? String) ?: "N/A"
        locationText.text = "📍 $building Room $room"

        this.roomId = roomID

        semesterText.text = "📅 $semester"
        creditsText.text = "📚 $credits Credits"
        departmentText.text = "🏫 $department"

        val enrolled = enrolledStudents.size
        enrollmentText.text = "👥 $enrolled / $maxCapacity students"
    }

    private fun buildScheduleString(schedule: List<Map<String, Any>>): String {
        if (schedule.isEmpty()) return "🕐 Schedule not available"

        val scheduleItems = schedule.mapNotNull { scheduleMap ->
            val dayOfWeek = scheduleMap["dayOfWeek"] as? String ?: return@mapNotNull null
            val startTime = scheduleMap["startTime"] as? String ?: return@mapNotNull null
            val endTime = scheduleMap["endTime"] as? String ?: return@mapNotNull null
            "$dayOfWeek $startTime - $endTime"
        }

        return if (scheduleItems.isEmpty()) {
            "🕐 Schedule not available"
        } else {
            "🕐 " + scheduleItems.joinToString("\n     ")
        }
    }

    private fun loadProfessorEmail(professorId: String) {
        if (professorId.isEmpty()) {
            android.util.Log.w("StudentCourseDetail", "Professor ID is empty!")
            return
        }

        db.collection("Users").document(professorId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    professorEmail = document.getString("email")
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentCourseDetail", "Error loading professor email: ${e.message}")
            }
    }

    private fun loadStudentAttendance(courseDocId: String) {
        val currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            android.util.Log.w("StudentCourseDetail", "No user logged in for attendance")
            return
        }

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
                } else {
                    val attendanceText = findViewById(R.id.attendanceRate)
                    attendanceText.text = "No attendance records yet"
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentCourseDetail", "Error loading attendance: ${e.message}")
            }
    }
}