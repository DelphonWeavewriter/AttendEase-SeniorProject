package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendRequestsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FriendRequestsAdapter
    private lateinit var backButton: TextView
    private lateinit var emptyText: TextView

    private val requestsList = mutableListOf<FriendRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_requests)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        backButton = findViewById(R.id.backButton)
        recyclerView = findViewById(R.id.requestsRecyclerView)
        emptyText = findViewById(R.id.emptyText)

        adapter = FriendRequestsAdapter(
            requestsList,
            onAccept = { request -> acceptRequest(request) },
            onDecline = { request -> declineRequest(request) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        backButton.setOnClickListener {
            finish()
        }

        loadRequests()
    }

    private fun loadRequests() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).collection("FriendRequests")
            .whereEqualTo("status", "pending")
            .whereEqualTo("type", "received")
            .get()
            .addOnSuccessListener { documents ->
                requestsList.clear()
                for (doc in documents) {
                    val request = doc.toObject(FriendRequest::class.java)
                    requestsList.add(request)
                }
                adapter.notifyDataSetChanged()

                if (requestsList.isEmpty()) {
                    emptyText.visibility = android.view.View.VISIBLE
                    recyclerView.visibility = android.view.View.GONE
                } else {
                    emptyText.visibility = android.view.View.GONE
                    recyclerView.visibility = android.view.View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading requests: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun acceptRequest(request: FriendRequest) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).collection("FriendRequests")
            .document(request.requestId)
            .update("status", "accepted")
            .addOnSuccessListener {
                addFriend(request)
                Toast.makeText(this, "Friend request accepted", Toast.LENGTH_SHORT).show()
                loadRequests()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error accepting request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun declineRequest(request: FriendRequest) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).collection("FriendRequests")
            .document(request.requestId)
            .update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(this, "Friend request declined", Toast.LENGTH_SHORT).show()
                loadRequests()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error declining request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addFriend(request: FriendRequest) {
        val userId = auth.currentUser?.uid ?: return

        val friend = Friend(
            friendId = request.fromUserId,
            friendName = request.fromUserName,
            friendMajor = request.fromUserMajor,
            friendProfilePic = request.fromUserProfilePic,
            status = "active",
            becameFriendsAt = System.currentTimeMillis()
        )

        db.collection("Users").document(userId).collection("Friends")
            .document(request.fromUserId)
            .set(friend)

        db.collection("Users").document(request.fromUserId)
            .get()
            .addOnSuccessListener { document ->
                val currentUser = document.toObject(User::class.java)
                currentUser?.let { user ->
                    val reciprocalFriend = Friend(
                        friendId = userId,
                        friendName = "${user.firstName} ${user.lastName}",
                        friendMajor = user.major,
                        friendProfilePic = user.profilePictureUrl,
                        status = "active",
                        becameFriendsAt = System.currentTimeMillis()
                    )

                    db.collection("Users").document(request.fromUserId).collection("Friends")
                        .document(userId)
                        .set(reciprocalFriend)
                }
            }
    }
}