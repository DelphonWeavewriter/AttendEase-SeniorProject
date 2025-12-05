package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendsListImplementation : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var addFriendTextField: EditText
    private lateinit var addFriendButton: Button
    private lateinit var adapter: FriendsAdapter

    private val friendsList = ArrayList<Friend>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.friends)

        recyclerView = findViewById(R.id.friendsRecycler)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = FriendsAdapter(friendsList) { friend ->
            // Optional: navigate to chat or profile
        }

        recyclerView.adapter = adapter

        loadFriends()

        addFriendButton.setOnClickListener {
            val uid = addFriendTextField.text.toString().trim()
            if (uid.isNotEmpty()) addFriend(uid)
        }
    }

    private fun loadFriends() {

        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users")
            .document(currentUserId)
            .collection("Friends")
            .get()
            .addOnSuccessListener { result ->
                friendsList.clear()

                for (doc in result) {
                    val friendId = doc.getString("userId") ?: ""
                    val first = doc.getString("firstName") ?: ""
                    val last = doc.getString("lastName") ?: ""
                    val major = doc.getString("major") ?: ""
                    val profilePic = doc.getString("profilePic") ?: ""



                    val friend = Friend(
                        friendId = friendId,
                        friendName = "$first $last",
                        friendMajor = major,
                        friendProfilePic = profilePic
                    )


                    friendsList.add(friend)
                }

                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e("FriendsList", "Error loading friends: ${e.message}")
            }
    }

    private fun addFriend(friendUid: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users")
            .whereEqualTo("userId", friendUid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Log.e("FriendsList", "No user found with UID $friendUid")
                    return@addOnSuccessListener
                }

                val friendData = snapshot.documents[0].data ?: return@addOnSuccessListener

                db.collection("Users")
                    .document(currentUserId)
                    .collection("Friends")
                    .document(friendUid)
                    .set(friendData)
                    .addOnSuccessListener {
                        loadFriends()
                    }
            }
    }
}
