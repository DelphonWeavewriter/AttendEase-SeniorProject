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
    private val allEvents = mutableListOf<Event>()
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
        loadScheduleData()
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

    private fun loadScheduleData() {
        val currentUserId = auth.currentUser?.uid ?: run {
            handleFailure("No user logged in!")
            return
        }
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        // Start the chain of loading
        loadCourses(currentUserId)
    }

    private fun loadCourses(userId: String) {
        db.collection("Users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                if (!userDoc.exists()) {
                    handleFailure("User document does not exist!")
                    return@addOnSuccessListener
                }

                val user = userDoc.toObject(User::class.java)
                val userRole = user?.role ?: "Student"

                val query = if (userRole == "Professor") {
                    db.collection("Courses").whereEqualTo("professorId", userId)
                } else {
                    db.collection("Courses").whereArrayContains("enrolledStudents", userId)
                }

                query.get()
                    .addOnSuccessListener { documents ->
                        allCourses.clear()
                        val courseIds = mutableListOf<String>()
                        for (document in documents) {
                            val course = document.toObject(Course::class.java)
                            allCourses.add(course)
                            course.courseId?.let { courseIds.add(it) }
                        }
                        // Continue the chain: load finals
                        loadFinals(userId, courseIds)
                    }
                    .addOnFailureListener { e -> handleFailure("Query failed: ${e.message}", e) }
            }
            .addOnFailureListener { e -> handleFailure("Failed to load user: ${e.message}", e) }
    }

    private fun loadFinals(userId: String, courseIds: List<String>) {
        if (courseIds.isEmpty()) {
            // No courses, so no finals to load. Skip to events.
            loadEvents(userId)
            return
        }

        db.collection("Finals").whereIn("courseId", courseIds).get()
            .addOnSuccessListener { documents ->
                allFinalExams.clear()
                for (document in documents) {
                    val finalExam = document.toObject(FinalExam::class.java)
                    allFinalExams.add(finalExam)
                }
                // Continue the chain: load events
                loadEvents(userId)
            }
            .addOnFailureListener { e -> handleFailure("Error loading finals: ${e.message}", e) }
    }

    private fun loadEvents(userId: String) {
        db.collection("Events").whereArrayContains("participants", userId).get()
            .addOnSuccessListener { documents ->
                allEvents.clear()
                for (document in documents) {
                    val event = document.toObject(Event::class.java)
                    allEvents.add(event)
                }
                // This is the final step, now update the UI
                filterScheduleByDate()
            }
            .addOnFailureListener { e -> handleFailure("Error loading events: ${e.message}", e) }
    }

    private fun handleFailure(message: String, e: Exception? = null) {
        android.util.Log.e("MySchedule", message, e)
        progressBar.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = message
    }

    private fun filterScheduleByDate() {
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(selectedDate.time)
        val selectedDateStr = SimpleDateFormat("M/d/yyyy", Locale.US).format(selectedDate.time)

        val scheduleItems = mutableListOf<ScheduleItem>()

        // Courses
        for (course in allCourses) {
            for (scheduleMap in course.schedule) {
                if (scheduleMap["dayOfWeek"]?.equals(dayOfWeek, ignoreCase = true) == true) {
                    scheduleItems.add(ScheduleItem(
                        title = course.courseName,
                        subtitle = course.courseId,
                        startTime = scheduleMap["startTime"] ?: "",
                        endTime = scheduleMap["endTime"] ?: "",
                        building = scheduleMap["building"] ?: "",
                        room = scheduleMap["room"] ?: ""
                    ))
                }
            }
        }

        // Finals
        for (finalExam in allFinalExams) {
            if (finalExam.date == selectedDateStr) {
                scheduleItems.add(ScheduleItem(
                    title = "Final Exam: ${finalExam.courseName}",
                    subtitle = finalExam.courseId,
                    startTime = finalExam.startTime,
                    endTime = finalExam.endTime,
                    building = finalExam.buildingId,
                    room = finalExam.roomId,
                    isFinalExam = true
                ))
            }
        }

        // Events
        for (event in allEvents) {
            for (scheduleItem in event.schedule) {
                val isOneTimeEvent = !scheduleItem.recurring && scheduleItem.date.isNotEmpty() && scheduleItem.date == selectedDateStr
                val isRecurringEvent = scheduleItem.recurring && scheduleItem.dayOfWeek.equals(dayOfWeek, ignoreCase = true)

                if (isOneTimeEvent || isRecurringEvent) {
                    val buildingDisplay = if (scheduleItem.building.isNotEmpty()) scheduleItem.building else if (scheduleItem.coordinates.isNotEmpty()) "Custom Location" else ""
                    val roomDisplay = if (scheduleItem.building.isNotEmpty()) scheduleItem.room else ""

                    scheduleItems.add(ScheduleItem(
                        title = event.description,
                        subtitle = "Event by ${event.creator}",
                        startTime = scheduleItem.startTime,
                        endTime = scheduleItem.endTime,
                        building = buildingDisplay,
                        room = roomDisplay,
                        isEvent = true
                    ))
                }
            }
        }

        scheduleItems.sortBy { it.startTime }

        progressBar.visibility = View.GONE
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

data class Event(
    val creator: String = "",
    val description: String = "",
    val participants: List<String> = listOf(),
    val private: Boolean = false,
    val schedule: List<EventSchedule> = listOf()
)

data class EventSchedule(
    val building: String = "",
    val coordinates: String = "",
    val date: String = "",
    val dayOfWeek: String = "",
    val endTime: String = "",
    val recurring: Boolean = false,
    val room: String = "",
    val startTime: String = ""
)

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
    val title: String,
    val subtitle: String,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val isFinalExam: Boolean = false,
    val isEvent: Boolean = false
)
