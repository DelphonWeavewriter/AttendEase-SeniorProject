package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyScheduleActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scheduleItems = mutableListOf<ScheduleItem>()
    private lateinit var adapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_schedule)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Schedule"

        recyclerView = findViewById(R.id.scheduleRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyText = findViewById(R.id.emptyText)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ScheduleAdapter(scheduleItems)
        recyclerView.adapter = adapter

        loadSchedule()
    }

    private fun loadSchedule() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("Courses")
            .whereEqualTo("professorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                scheduleItems.clear()

                for (doc in documents) {
                    val course = doc.toObject(Course::class.java)
                    for (classSchedule in course.schedule) {
                        scheduleItems.add(
                            ScheduleItem(
                                courseCode = course.courseCode,
                                courseName = course.courseName,
                                dayOfWeek = classSchedule.dayOfWeek,
                                startTime = classSchedule.startTime,
                                endTime = classSchedule.endTime,
                                room = classSchedule.room,
                                building = classSchedule.building
                            )
                        )
                    }
                }

                scheduleItems.sortWith(
                    compareBy<ScheduleItem> { dayOrder(it.dayOfWeek) }
                        .thenBy { it.startTime }
                )

                adapter.notifyDataSetChanged()

                if (scheduleItems.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                }

                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun dayOrder(day: String): Int {
        return when (day.uppercase()) {
            "MONDAY", "MON" -> 1
            "TUESDAY", "TUE" -> 2
            "WEDNESDAY", "WED" -> 3
            "THURSDAY", "THU" -> 4
            "FRIDAY", "FRI" -> 5
            "SATURDAY", "SAT" -> 6
            "SUNDAY", "SUN" -> 7
            else -> 8
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

data class ScheduleItem(
    val courseCode: String = "",
    val courseName: String = "",
    val dayOfWeek: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val room: String = "",
    val building: String = ""
)