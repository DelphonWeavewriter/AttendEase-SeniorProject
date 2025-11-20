package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class NotificationsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: TextView
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var emptyText: TextView

    private lateinit var adapter: NotificationsAdapter
    private val notificationsList = mutableListOf<Notification>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        backButton = findViewById(R.id.backButton)
        notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView)
        emptyText = findViewById(R.id.emptyText)

        setupRecyclerView()
        loadNotifications()

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationsAdapter(notificationsList)
        notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        notificationsRecyclerView.adapter = adapter
    }

    private fun loadNotifications() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).collection("Notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                notificationsList.clear()
                for (doc in documents) {
                    val notification = doc.toObject(Notification::class.java)
                    notificationsList.add(notification)
                }
                adapter.notifyDataSetChanged()

                markAllAsRead()

                if (notificationsList.isEmpty()) {
                    emptyText.visibility = android.view.View.VISIBLE
                    notificationsRecyclerView.visibility = android.view.View.GONE
                } else {
                    emptyText.visibility = android.view.View.GONE
                    notificationsRecyclerView.visibility = android.view.View.VISIBLE
                }
            }
    }

    private fun markAllAsRead() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).collection("Notifications")
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    doc.reference.update("read", true)
                }
            }
    }
}