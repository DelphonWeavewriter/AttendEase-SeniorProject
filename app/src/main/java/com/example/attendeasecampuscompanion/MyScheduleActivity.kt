package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.view.View
import android.widget.CalendarView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MyScheduleActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var dateText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val allCourses = mutableListOf<Course>()
    private val allFinalExams = mutableListOf<FinalExam>()
    private lateinit var scheduleAdapter: ScheduleAdapter
    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_schedule)

        supportActionBar?.hide()

        calendarView = findViewById(R.id.calendarView)
        dateText = findViewById(R.id.dateText)
        recyclerView = findViewById(R.id.recyclerViewSchedule)
        progressBar = findViewById(R.id.progressBar)
        emptyText = findViewById(R.id.emptyText)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        setupRecyclerView()
        setupCalendar()
        loadCoursesAndFinals()
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = scheduleAdapter
    }

    private fun setupCalendar() {
        updateDateText()

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate.set(year, month, dayOfMonth)
            updateDateText()
            filterScheduleByDate()
        }
    }

    private fun updateDateText() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        dateText.text = dateFormat.format(selectedDate.time)
    }

    private fun loadCoursesAndFinals() {
        val currentUserId = auth.currentUser?.uid ?: run {

            android.util.Log.e("MySchedule", "No user logged in!")

            return

        }



        android.util.Log.d("MySchedule", "Starting loadCourses for UID: $currentUserId")

        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        db.collection("Users").document(currentUserId).get()
            .addOnSuccessListener { userDoc ->
                android.util.Log.d("MySchedule", "User document loaded successfully")
                if (!userDoc.exists()) {
                    android.util.Log.e("MySchedule", "User document does not exist!")
                    progressBar.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "User not found"
                    return@addOnSuccessListener
                }

                val user = userDoc.toObject(User::class.java)
                val userRole = user?.role ?: "Student"

                android.util.Log.d("MySchedule", "User role: $userRole")
                android.util.Log.d("MySchedule", "User name: ${user?.firstName} ${user?.lastName}")

                val query = if (userRole == "Professor") {
                    android.util.Log.d("MySchedule", "Querying as Professor")
                    db.collection("Courses").whereEqualTo("professorId", currentUserId)
                } else {
                    android.util.Log.d("MySchedule", "Querying as Student with enrolledStudents")
                    db.collection("Courses").whereArrayContains("enrolledStudents", currentUserId)
                }

                query.get()
                    .addOnSuccessListener { documents ->
                        android.util.Log.d("MySchedule", "Query successful! Found ${documents.size()} courses")
                        progressBar.visibility = View.GONE
                        allCourses.clear()
                        val courseIds = mutableListOf<String>()
                        for (document in documents) {
                            val course = document.toObject(Course::class.java)
                            android.util.Log.d("MySchedule", "Course found: ${course.courseId} - ${course.courseName}")
                            allCourses.add(course)
                            course.courseId?.let { courseIds.add(it) }
                        }
                        loadFinals(courseIds)
                        android.util.Log.d("MySchedule", "Filtering schedule by date...")
                        filterScheduleByDate()
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("MySchedule", "Query failed: ${e.message}", e)
                        progressBar.visibility = View.GONE
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "Error loading schedule: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MySchedule", "Failed to load user: ${e.message}", e)
                progressBar.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = "Error loading user data: ${e.message}"
            }
    }

    private fun loadFinals(courseIds: List<String>) {
        if (courseIds.isEmpty()) {
            progressBar.visibility = View.GONE
            filterScheduleByDate()
            return
        }

        db.collection("Finals").whereIn("courseId", courseIds).get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                allFinalExams.clear()
                for (document in documents) {
                    val finalExam = document.toObject(FinalExam::class.java)
                    allFinalExams.add(finalExam)
                }
                filterScheduleByDate()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = "Error loading finals: ${e.message}"
            }
    }

    private fun filterScheduleByDate() {
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(selectedDate.time)
        val selectedDateStr = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(selectedDate.time)

        val scheduleItems = mutableListOf<ScheduleItem>()

        for (course in allCourses) {
            for (scheduleMap in course.schedule) {
                val scheduleDayOfWeek = scheduleMap["dayOfWeek"] ?: continue
                if (scheduleDayOfWeek.equals(dayOfWeek, ignoreCase = true)) {
                    val item = ScheduleItem(
                        courseName = course.courseName,
                        courseCode = course.courseId,
                        startTime = scheduleMap["startTime"] ?: "",
                        endTime = scheduleMap["endTime"] ?: "",
                        building = scheduleMap["building"] ?: "",
                        room = scheduleMap["room"] ?: "",
                        roomID = course.roomID
                    )
                    scheduleItems.add(item)
                }
            }
        }

        for (finalExam in allFinalExams) {
            if (finalExam.date == selectedDateStr) {
                val item = ScheduleItem(
                    courseName = finalExam.courseName,
                    courseCode = finalExam.courseId,
                    startTime = finalExam.startTime,
                    endTime = finalExam.endTime,
                    building = finalExam.buildingId,
                    room = finalExam.roomId,
                    roomID = "", //RoomID handled above, seems redundant but ill leave it for now
                    isFinalExam = true
                )
                scheduleItems.add(item)
            }
        }

        scheduleItems.sortBy { it.startTime }

        if (scheduleItems.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "No classes, exams, or events scheduled for this day"
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            scheduleAdapter.updateSchedule(scheduleItems)
        }
    }
}

data class FinalExam(
    val buildingId: String = "",
    val roomId: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = ""
)

data class ScheduleItem(
    val courseName: String,
    val courseCode: String,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val roomID: String,
    val isFinalExam: Boolean = false
)
