package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendsListActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FriendsAdapter
    private lateinit var backButton: TextView
    private lateinit var emptyText: TextView

    private val friendsList = mutableListOf<Friend>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends_list)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        backButton = findViewById(R.id.backButton)
        recyclerView = findViewById(R.id.friendsRecyclerView)
        emptyText = findViewById(R.id.emptyText)

        adapter = FriendsAdapter(friendsList) { friend ->
            Toast.makeText(this, "Clicked ${friend.friendName}", Toast.LENGTH_SHORT).show()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        backButton.setOnClickListener {
            finish()
        }

        loadFriends()
    }

    private fun loadFriends() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).collection("Friends")
            .get()
            .addOnSuccessListener { documents ->
                friendsList.clear()
                for (doc in documents) {
                    val friend = doc.toObject(Friend::class.java)
                    friendsList.add(friend)
                }
                adapter.notifyDataSetChanged()

                if (friendsList.isEmpty()) {
                    emptyText.visibility = android.view.View.VISIBLE
                    recyclerView.visibility = android.view.View.GONE
                } else {
                    emptyText.visibility = android.view.View.GONE
                    recyclerView.visibility = android.view.View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading friends: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}