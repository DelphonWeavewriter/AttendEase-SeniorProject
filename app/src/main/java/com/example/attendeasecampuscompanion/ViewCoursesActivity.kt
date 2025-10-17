package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewCoursesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CourseAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val courses = mutableListOf<Course>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_courses)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Courses"

        recyclerView = findViewById(R.id.coursesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = CourseAdapter(courses) { course ->
            showCourseDetails(course)
        }
        recyclerView.adapter = adapter

        loadCourses()
    }

    private fun loadCourses() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Courses")
            .whereIn("professorId", listOf(userId))
            .get()
            .addOnSuccessListener { docs1 ->
                courses.clear()
                for (doc in docs1) {
                    val course = doc.toObject(Course::class.java)
                    courses.add(course)
                }

                db.collection("Courses")
                    .whereIn("professorID", listOf(userId))
                    .get()
                    .addOnSuccessListener { documents ->
                        courses.clear()
                        for (doc in documents) {
                            val course = doc.toObject(Course::class.java)
                            courses.add(course)
                        }
                        adapter.notifyDataSetChanged()

                        if (courses.isEmpty()) {
                            Toast.makeText(this, "No courses found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error loading courses: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }

        private fun showCourseDetails(course: Course) {
            progressBar.visibility = View.VISIBLE

            db.collection("Enrollments")
                .whereEqualTo("courseId", course.courseId)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .addOnSuccessListener { enrollmentDocs ->
                    val enrollmentCount = enrollmentDocs.size()

                    val message = buildString {
                        append("Course: ${course.courseCode}\n")
                        append("Name: ${course.courseName}\n")
                        append("Department: ${course.department}\n")
                        append("Semester: ${course.semester}\n")
                        append("Room: ${course.room}\n")
                        append("Credits: ${course.credits}\n")
                        append("Enrolled: $enrollmentCount/${course.maxCapacity}\n\n")
                        append("Schedule:\n")
                        course.schedule.forEach { sched ->
                            append("${sched.dayOfWeek}: ${sched.startTime} - ${sched.endTime}\n")
                        }
                    }

                    progressBar.visibility = View.GONE

                    AlertDialog.Builder(this)
                        .setTitle("Course Details")
                        .setMessage(message)
                        .setPositiveButton("View Roster") { _, _ ->
                            showRoster(course)
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error loading course details: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        private fun showRoster(course: Course) {
            progressBar.visibility = View.VISIBLE

            db.collection("Enrollments")
                .whereEqualTo("courseId", course.courseId)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Toast.makeText(this, "No students enrolled", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        return@addOnSuccessListener
                    }

                    val students = documents.map { doc ->
                        val enrollment = doc.toObject(Enrollment::class.java)
                        enrollment.studentName
                    }.sorted().joinToString("\n")

                    progressBar.visibility = View.GONE

                    AlertDialog.Builder(this)
                        .setTitle("Class Roster - ${course.courseCode}")
                        .setMessage(students)
                        .setPositiveButton("Close", null)
                        .show()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error loading roster: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        override fun onSupportNavigateUp(): Boolean {
            finish()
            return true
        }
    }