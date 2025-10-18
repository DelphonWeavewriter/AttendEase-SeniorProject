package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewCoursesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var courseAdapter: CourseAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val coursesList = mutableListOf<Course>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_courses)

        recyclerView = findViewById(R.id.recyclerViewCourses)
        progressBar = findViewById(R.id.progressBar)
        emptyText = findViewById(R.id.emptyText)

        setupRecyclerView()
        loadCourses()
    }

    private fun setupRecyclerView() {
        courseAdapter = CourseAdapter(coursesList) { course ->
            showCourseDetails(course)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = courseAdapter
    }

    private fun loadCourses() {
        val professorId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        db.collection("Courses")
            .whereEqualTo("professorId", professorId)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                coursesList.clear()

                for (document in documents) {
                    val course = document.toObject(Course::class.java)
                    coursesList.add(course)
                }

                if (coursesList.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    courseAdapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener { exception ->
                progressBar.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                Toast.makeText(this, "Error loading courses: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCourseDetails(course: Course) {
        if (course.enrolledStudents.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(course.courseName)
                .setMessage("No students enrolled yet")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val studentIds = course.enrolledStudents
        val studentNames = mutableListOf<String>()
        var loadedCount = 0

        progressBar.visibility = View.VISIBLE

        for (studentId in studentIds) {
            db.collection("Users").document(studentId).get()
                .addOnSuccessListener { document ->
                    val user = document.toObject(User::class.java)
                    if (user != null) {
                        studentNames.add("${user.firstName} ${user.lastName}")
                    }
                    loadedCount++

                    if (loadedCount == studentIds.size) {
                        progressBar.visibility = View.GONE
                        showRosterDialog(course, studentNames)
                    }
                }
                .addOnFailureListener {
                    loadedCount++
                    if (loadedCount == studentIds.size) {
                        progressBar.visibility = View.GONE
                        showRosterDialog(course, studentNames)
                    }
                }
        }
    }

    private fun showRosterDialog(course: Course, studentNames: List<String>) {
        val message = if (studentNames.isEmpty()) {
            "No student information available"
        } else {
            studentNames.joinToString("\n")
        }

        AlertDialog.Builder(this)
            .setTitle("${course.courseName} Roster")
            .setMessage("Enrolled Students (${studentNames.size}/${course.maxCapacity}):\n\n$message")
            .setPositiveButton("OK", null)
            .show()
    }
}