package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendeasecampuscompanion.adapters.FriendAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendsListImplementation : ComponentActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var addFriendTextField: EditText
    private lateinit var addFriendButton: Button

    private val friendsArray = ArrayList<FriendMSG>()
    private lateinit var adapter: FriendAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.friends)

        recyclerView = findViewById(R.id.friendsRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = FriendAdapter(
            friendsArray,
            onSwitchToggle = { friend, isOn ->
                Toast.makeText(this, "Location for ${friend.fullName}: $isOn", Toast.LENGTH_SHORT).show()
            },
            onButtonClick = { friend ->
                val intent = Intent(this, MessageActivity::class.java)
                intent.putExtra("FRIEND_UID", friend.uid)
                intent.putExtra("FRIEND_NAME", friend.fullName)
                startActivity(intent)
            }
        )

        recyclerView.adapter = adapter

        loadFriends()

        addFriendButton.setOnClickListener {
            val uid = addFriendTextField.text.toString().trim()
            if (uid.isNotEmpty()) {
                addFriend(uid)
            } else {
                Toast.makeText(this, "Enter a UID", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadFriends() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users").document(currentUserId)
            .collection("FriendsList")
            .get()
            .addOnSuccessListener { result ->
                friendsArray.clear()

                for (document in result) {
                    val first = document.getString("firstName") ?: "Unknown"
                    val last = document.getString("lastName") ?: ""
                    val uid = document.getString("userId") ?: ""

                    friendsArray.add(
                        FriendMSG("$first $last", uid)
                    )
                }

                adapter.notifyDataSetChanged()
            }
    }

    private fun addFriend(friendUid: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users")
            .whereEqualTo("userId", friendUid)
            .get()
            .addOnSuccessListener { qs ->
                if (!qs.isEmpty) {
                    val friendData = qs.documents[0].data!!

                    db.collection("Users")
                        .document(currentUserId)
                        .collection("FriendsList")
                        .document(friendUid)
                        .set(friendData)

                    Toast.makeText(this, "Friend added!", Toast.LENGTH_SHORT).show()
                    loadFriends()
                } else {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
