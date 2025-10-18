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
        loadCourses()
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

    private fun loadCourses() {
        val currentUserId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        db.collection("Users").document(currentUserId).get()
            .addOnSuccessListener { userDoc ->
                val user = userDoc.toObject(User::class.java)
                val userRole = user?.role ?: "Student"

                val query = if (userRole == "Professor") {
                    db.collection("Courses").whereEqualTo("professorId", currentUserId)
                } else {
                    db.collection("Courses").whereArrayContains("enrolledStudents", currentUserId)
                }

                query.get()
                    .addOnSuccessListener { documents ->
                        progressBar.visibility = View.GONE
                        allCourses.clear()

                        for (document in documents) {
                            val course = document.toObject(Course::class.java)
                            allCourses.add(course)
                        }

                        filterScheduleByDate()
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "Error loading schedule"
                    }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = "Error loading user data"
            }
    }

    private fun filterScheduleByDate() {
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(selectedDate.time)
        val scheduleItems = mutableListOf<ScheduleItem>()

        for (course in allCourses) {
            for (scheduleMap in course.schedule) {
                val scheduleDayOfWeek = scheduleMap["dayOfWeek"] ?: continue

                if (scheduleDayOfWeek.equals(dayOfWeek, ignoreCase = true)) {
                    scheduleItems.add(
                        ScheduleItem(
                            courseName = course.courseName,
                            courseCode = course.courseId,
                            startTime = scheduleMap["startTime"] ?: "",
                            endTime = scheduleMap["endTime"] ?: "",
                            building = scheduleMap["building"] ?: "",
                            room = scheduleMap["room"] ?: "",
                            roomID = course.roomID
                        )
                    )
                }
            }
        }

        scheduleItems.sortBy { it.startTime }

        if (scheduleItems.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "No classes scheduled for this day"
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            scheduleAdapter.updateSchedule(scheduleItems)
        }
    }
}

data class ScheduleItem(
    val courseName: String,
    val courseCode: String,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val roomID: String
)