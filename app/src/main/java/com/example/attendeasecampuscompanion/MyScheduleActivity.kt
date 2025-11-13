package com.example.attendeasecampuscompanion

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MyScheduleActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var dateText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var fabCreateEvent: FloatingActionButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val allCourses = mutableListOf<Course>()
    private val allFinalExams = mutableListOf<FinalExam>()
    private val allEvents = mutableListOf<Event>()
    private lateinit var scheduleAdapter: ScheduleAdapter
    private var selectedDate: Calendar = Calendar.getInstance()
    private var userRole: String = "Student"
    private var userCampus: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_schedule)

        supportActionBar?.hide()

        calendarView = findViewById(R.id.calendarView)
        dateText = findViewById(R.id.dateText)
        recyclerView = findViewById(R.id.recyclerViewSchedule)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        fabCreateEvent = findViewById(R.id.fabCreateEvent)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        fabCreateEvent.setOnClickListener {
            showEventDialog()
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
        emptyState.visibility = View.GONE
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
                userRole = user?.role ?: "Student"
                userCampus = user?.campus ?: ""

                fabCreateEvent.visibility = if (userRole == "Professor") View.VISIBLE else View.GONE

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
                        loadFinals(userId, courseIds)
                    }
                    .addOnFailureListener { e -> handleFailure("Query failed: ${e.message}", e) }
            }
            .addOnFailureListener { e -> handleFailure("Failed to load user: ${e.message}", e) }
    }

    private fun loadFinals(userId: String, courseIds: List<String>) {
        if (courseIds.isEmpty()) {
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
                filterScheduleByDate()
            }
            .addOnFailureListener { e -> handleFailure("Error loading events: ${e.message}", e) }
    }

    private fun handleFailure(message: String, e: Exception? = null) {
        android.util.Log.e("MySchedule", message, e)
        progressBar.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
    }

    private fun filterScheduleByDate() {
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(selectedDate.time)
        val selectedDateStr = SimpleDateFormat("M/d/yyyy", Locale.US).format(selectedDate.time)
        val dateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)

        val scheduleItems = mutableListOf<ScheduleItem>()

        fun isDateInRange(startDateStr: String, endDateStr: String, selectedDateStr: String): Boolean {
            if (startDateStr.isEmpty() || endDateStr.isEmpty()) return true
            return try {
                val startCal = Calendar.getInstance().apply { time = dateFormat.parse(startDateStr)!! }
                val endCal = Calendar.getInstance().apply { time = dateFormat.parse(endDateStr)!! }
                val selectedCal = Calendar.getInstance().apply { time = dateFormat.parse(selectedDateStr)!! }

                fun Calendar.clearTime() = apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                !selectedCal.clearTime().before(startCal.clearTime()) && !selectedCal.clearTime().after(endCal.clearTime())
            } catch (e: ParseException) {
                android.util.Log.e("MySchedule", "Date parsing failed", e)
                false
            }
        }

        for (course in allCourses) {
            if (isDateInRange(course.semesterStart, course.semesterEnd, selectedDateStr)) {
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
        }

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

        for (event in allEvents) {
            for (scheduleItem in event.schedule) {
                val isOneTimeEvent = !scheduleItem.recurring && scheduleItem.date.isNotEmpty() && scheduleItem.date == selectedDateStr
                val isRecurringEvent = scheduleItem.recurring && scheduleItem.dayOfWeek.equals(dayOfWeek, ignoreCase = true)

                var inRange = true
                if (isRecurringEvent && event.courseId.isNotEmpty()) {
                    val linkedCourse = allCourses.find { it.courseId == event.courseId }
                    if (linkedCourse != null) {
                        inRange = isDateInRange(linkedCourse.semesterStart, linkedCourse.semesterEnd, selectedDateStr)
                    }
                }

                if (inRange && (isOneTimeEvent || isRecurringEvent)) {
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
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            scheduleAdapter.updateSchedule(scheduleItems)
        }
    }

    private fun showEventDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_event, null)
        val eventNameInput = dialogView.findViewById<EditText>(R.id.etEventName)
        val eventLocationInput = dialogView.findViewById<EditText>(R.id.etEventLocation)
        val eventDateInput = dialogView.findViewById<EditText>(R.id.etEventDate)
        val eventTimeInput = dialogView.findViewById<EditText>(R.id.etEventTime)
        val eventDescriptionInput = dialogView.findViewById<EditText>(R.id.etEventDescription)
        val courseSpinner = dialogView.findViewById<Spinner>(R.id.spinnerEventCourse)

        loadCoursesForSpinner(courseSpinner)

        val calendar = Calendar.getInstance()
        var selectedDateStr = ""
        var selectedTime = ""

        eventDateInput.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDateStr = "${month + 1}/$day/$year"
                    eventDateInput.setText(selectedDateStr)
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
                    selectedTime = String.format("%d:%02d %s", displayHour, minute, amPm)
                    eventTimeInput.setText(selectedTime)
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
                val description = eventDescriptionInput.text.toString()
                val date = selectedDateStr
                val time = selectedTime
                val selectedCourseItem = courseSpinner.selectedItem as? CourseSpinnerItem
                val courseId = selectedCourseItem?.courseId ?: ""

                if (name.isNotBlank() && date.isNotBlank() && time.isNotBlank()) {
                    saveEventToFirebase(name, description, location, date, time, courseId)
                } else {
                    Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCoursesForSpinner(spinner: Spinner) {
        val courseItems = mutableListOf<CourseSpinnerItem>()
        courseItems.add(CourseSpinnerItem("", "No Course"))

        for (course in allCourses) {
            courseItems.add(CourseSpinnerItem(course.courseId ?: "", "${course.courseId} - ${course.courseName}"))
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            courseItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun saveEventToFirebase(
        name: String,
        description: String,
        location: String,
        date: String,
        time: String,
        courseId: String
    ) {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("Users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val user = userDoc.toObject(User::class.java)
                val creatorName = "${user?.firstName} ${user?.lastName}"

                val participants = mutableListOf(userId)

                if (courseId.isNotEmpty()) {
                    val linkedCourse = allCourses.find { it.courseId == courseId }
                    linkedCourse?.enrolledStudents?.let { participants.addAll(it) }
                }

                val eventData = hashMapOf(
                    "campus" to userCampus,
                    "creator" to creatorName,
                    "description" to if (description.isNotEmpty()) description else name,
                    "participants" to participants,
                    "private" to false,
                    "courseId" to courseId,
                    "schedule" to listOf(
                        hashMapOf(
                            "building" to location,
                            "coordinates" to "",
                            "date" to date,
                            "dayOfWeek" to "",
                            "endTime" to "",
                            "recurring" to false,
                            "room" to "",
                            "startTime" to time
                        )
                    )
                )

                db.collection("Events")
                    .add(eventData)
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Event created successfully!", Toast.LENGTH_SHORT).show()
                        loadScheduleData()
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Failed to create event: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

data class CourseSpinnerItem(
    val courseId: String,
    val displayName: String
) {
    override fun toString(): String = displayName
}

data class Event(
    val campus: String = "",
    val creator: String = "",
    val description: String = "",
    val participants: List<String> = listOf(),
    val private: Boolean = false,
    val schedule: List<EventSchedule> = listOf(),
    val courseId: String = ""
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