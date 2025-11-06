package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ViewAnnouncementsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AnnouncementAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: TextView
    private lateinit var courseTitle: TextView

    private var courseDocId: String = ""
    private var courseName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_announcements)

        db = FirebaseFirestore.getInstance()

        courseDocId = intent.getStringExtra("courseDocId") ?: ""
        courseName = intent.getStringExtra("courseName") ?: ""

        if (courseDocId.isEmpty()) {
            Toast.makeText(this, "Error: Course not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        loadAnnouncements()
    }

    private fun initializeViews() {
        courseTitle = findViewById(R.id.tvCourseName)
        recyclerView = findViewById(R.id.rvAnnouncements)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.tvEmptyState)

        courseTitle.text = courseName

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = AnnouncementAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadAnnouncements() {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        recyclerView.visibility = View.GONE

        db.collection("Courses")
            .document(courseDocId)
            .collection("Announcements")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                val announcements = documents.mapNotNull { doc ->
                    try {
                        Announcement(
                            announcementId = doc.id,
                            title = doc.getString("title") ?: "",
                            message = doc.getString("message") ?: "",
                            courseId = doc.getString("courseId") ?: "",
                            courseName = doc.getString("courseName") ?: "",
                            createdBy = doc.getString("createdBy") ?: "",
                            createdByName = doc.getString("createdByName") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            priority = doc.getString("priority") ?: "NORMAL"
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                if (announcements.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(announcements)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                emptyState.text = "Error loading announcements"
                Toast.makeText(
                    this,
                    "Failed to load announcements: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}