package com.example.attendeasecampuscompanion

import android.content.Intent
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
    private lateinit var emptyState: View
    private lateinit var courseAdapter: CourseAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val coursesList = mutableListOf<Course>()
    private val courseDocIds = mutableListOf<String>()

    private var userRole: String = "Student"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_courses)

        recyclerView = findViewById(R.id.recyclerViewCourses)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        setupRecyclerView()
        loadCourses()
    }

    private fun setupRecyclerView() {
        courseAdapter = CourseAdapter(coursesList) { course ->
            handleCourseClick(course)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = courseAdapter
    }

    private fun loadCourses() {
        val currentUserId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        db.collection("Users").document(currentUserId).get()
            .addOnSuccessListener { userDoc ->
                if (!userDoc.exists()) {
                    progressBar.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                val user = userDoc.toObject(User::class.java)
                userRole = user?.role ?: "Student"

                val query = if (userRole == "Professor") {
                    db.collection("Courses").whereEqualTo("professorId", currentUserId)
                } else {
                    db.collection("Courses").whereArrayContains("enrolledStudents", currentUserId)
                }

                query.get()
                    .addOnSuccessListener { documents ->
                        progressBar.visibility = View.GONE
                        coursesList.clear()
                        courseDocIds.clear()

                        for (document in documents) {
                            val course = document.toObject(Course::class.java)
                            coursesList.add(course)
                            courseDocIds.add(document.id)
                        }

                        if (coursesList.isEmpty()) {
                            emptyState.visibility = View.VISIBLE
                        } else {
                            courseAdapter.notifyDataSetChanged()
                        }
                    }
                    .addOnFailureListener { exception ->
                        progressBar.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                        Toast.makeText(this, "Error loading courses: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { exception ->
                progressBar.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                Toast.makeText(this, "Error loading user: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleCourseClick(course: Course) {
        if (userRole == "Professor") {
            showCourseRoster(course)
        } else {
            openCourseDetails(course)
        }
    }

    private fun showCourseRoster(course: Course) {
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

    private fun openCourseDetails(course: Course) {
        val position = coursesList.indexOf(course)
        if (position != -1) {
            val courseDocId = courseDocIds[position]
            val intent = Intent(this, StudentCourseDetailActivity::class.java).apply {
                putExtra("courseId", course.courseId)
                putExtra("courseDocId", courseDocId)
            }
            startActivity(intent)
        }
    }
}