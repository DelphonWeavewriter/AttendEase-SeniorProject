package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FriendsListImplementation : ComponentActivity() {



    private lateinit var auth: FirebaseAuth

    private var currentUser: FirebaseUser? = null
    private lateinit var friendsListView: ListView
    private val db = FirebaseFirestore.getInstance()
    private val friendsArray = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var addFriendTextField: EditText
    private lateinit var addFriendButton: Button


    data class Friend(
        val studentId: String,
        val name: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.friends)
        auth = FirebaseAuth.getInstance()
        //db = FirebaseFirestore.getInstance()
        val user = auth.currentUser

        //val friendsList = findViewById<ListView>(R.id.friendsList)
        //getFriendsDetailed()

        addFriendTextField = findViewById(R.id.addFriendTextField)
        addFriendButton = findViewById(R.id.addFriendButton)
        friendsListView = findViewById(R.id.friendsList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, friendsArray)
        friendsListView.adapter = adapter

        loadFriends()

        addFriendButton.setOnClickListener {
            val friendUid = addFriendTextField.text.toString().trim()
            if (friendUid.isNotEmpty()) {
                addFriend(friendUid)
            } else {
                Toast.makeText(this, "Please enter a valid UID", Toast.LENGTH_SHORT).show()
            }
        }

    }

    override fun onStart() {
        super.onStart()
        currentUser = auth.currentUser
    }

    private fun loadFriends() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users").document(currentUserId)
            .collection("FriendsList")
            .get()
            .addOnSuccessListener { result ->
                friendsArray.clear()
                for (document in result) {
                    val firstName = document.getString("firstName") ?: "Unknown"
                    val lastName = document.getString("lastName") ?: ""
                    val userId = document.getString("userId") ?: "No ID"

                    val displayText = "$firstName $lastName  (UID: $userId)"
                    friendsArray.add(displayText)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                friendsArray.add("Error loading data: ${e.message}")
                adapter.notifyDataSetChanged()
            }
    }
    private fun addFriend(friendUid: String) {
        // Check that the friend exists in Users collection
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users")
            .whereEqualTo("userId", friendUid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val friendData = document.data

                    db.collection("Users")
                        .document(currentUserId)
                        .collection("FriendsList")
                        .document(friendUid)
                        .set(friendData!!)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Friend added!", Toast.LENGTH_SHORT).show()
                            loadFriends()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error adding friend: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "No user found with that UID.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error finding user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    /*
    /** Adds the given studentID to the current user's `friends` array */
    fun addFriendByStudentId(studentId: String, onDone: (Boolean, String?) -> Unit) {
        val uid = currentUser?.uid ?: return onDone(false, "No signed-in user")
        if (studentId.isBlank()) return onDone(false, "studentID is empty")

        // Validate the friend exists
        db.collection(USERS)
            .whereEqualTo(STUDENT_ID_FIELD, studentId)
            .limit(1)
            .get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) {
                    onDone(false, "No user found with that studentID")
                    return@addOnSuccessListener
                }

                db.collection(USERS).document(uid)
                    .update(FRIENDS_FIELD, FieldValue.arrayUnion(studentId))
                    .addOnSuccessListener {
                        Log.d(TAG, "Added $studentId to friends list")
                        onDone(true, null)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to add friend", e)
                        onDone(false, e.localizedMessage)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Validation query failed", e)
                onDone(false, e.localizedMessage)
            }
    }

    /** Removes a friend (by studentID) from the user's friends array */
    fun removeFriendByStudentId(studentId: String, onDone: (Boolean, String?) -> Unit) {
        val uid = currentUser?.uid ?: return onDone(false, "No signed-in user")
        db.collection(USERS).document(uid)
            .update(FRIENDS_FIELD, FieldValue.arrayRemove(studentId))
            .addOnSuccessListener {
                Log.d(TAG, "Removed $studentId from friends list")
                onDone(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove friend", e)
                onDone(false, e.localizedMessage)
            }
    }

    /** Retrieves the list of friend student IDs */
    fun getFriendStudentIds(onResult: (List<String>, String?) -> Unit) {
        val uid = currentUser?.uid ?: return onResult(emptyList(), "No signed-in user")
        db.collection(USERS).document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val ids = (doc.get(FRIENDS_FIELD) as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()
                onResult(ids, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get friends list", e)
                onResult(emptyList(), e.localizedMessage)
            }
    }

    /** Gets full friend details (studentID → name) */
    // fun getFriendsDetailed(onResult: (List<Friend>, String?) -> Unit) {
    fun getFriendsDetailed() {
        getFriendStudentIds { ids, err ->
            if (err != null || ids.isEmpty()) {
                Log.e(TAG, "Error getting friend IDs: $err")
                return@getFriendStudentIds
            }

            val chunks = ids.chunked(10) // Firestore whereIn limit
            val results = mutableListOf<Friend>()
            var completed = 0
            var failed: String? = null

            chunks.forEach { chunk ->
                db.collection(USERS)
                    .whereIn(STUDENT_ID_FIELD, chunk)
                    .get()
                    .addOnSuccessListener { snap ->
                        for (doc in snap.documents) {
                            val sid = doc.getString(STUDENT_ID_FIELD) ?: continue
                            val first = doc.getString(FIRST_NAME_FIELD).orEmpty()
                            val last = doc.getString(LAST_NAME_FIELD).orEmpty()
                            val name =
                                listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
                            results.add(Friend(studentId = sid, name = name.ifBlank { null }))
                        }
                        completed++
                        if (completed == chunks.size) {
                            updateFriendsList(results)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to resolve friend names", e)
                        failed = e.localizedMessage
                        completed++
                        if (completed == chunks.size) {
                            updateFriendsList(results)
                        }
                    }
            }
        }
    }


            private fun updateFriendsList(results: List<Friend>) {
                val friendsList = findViewById<ListView>(R.id.friendsList)
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    results.map { it.name ?: it.studentId }
                )
                friendsList.adapter = adapter
            }

            /** Searches friends by name or studentID */
            /* fun searchFriends(query: String, onResult: (List<Friend>, String?) -> Unit) {
        val q = query.trim()
        if (q.isEmpty()) {
            getFriendsDetailed(onResult)
            return
        }

        getFriendsDetailed { list, err ->
            if (err != null) return@getFriendsDetailed onResult(emptyList(), err)
            val filtered = list.filter {
                it.studentId.contains(q, ignoreCase = true) ||
                        (it.name?.contains(q, ignoreCase = true) == true)
            }
            onResult(filtered, null)
        }*/
        */

}