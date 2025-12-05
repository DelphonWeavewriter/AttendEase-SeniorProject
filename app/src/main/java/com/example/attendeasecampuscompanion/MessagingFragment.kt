package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MessagingFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FriendsAdapter
    private val friendsList = ArrayList<Friend>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messaging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.messagingRecycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FriendsAdapter(friendsList) { friend ->
            // Launch chat screen
            val intent = Intent(requireContext(), MessageActivity::class.java)
            intent.putExtra("FRIEND_ID", friend.friendId)
            intent.putExtra("FRIEND_NAME", friend.friendName)
            startActivity(intent)
        }

        recyclerView.adapter = adapter

        loadFriends()
    }

    private fun loadFriends() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("Users")
            .document(uid)
            .collection("Friends")
            .get()
            .addOnSuccessListener { result ->
                friendsList.clear()

                for (doc in result) {
                    val friendId = doc.getString("userId") ?: ""
                    val first = doc.getString("firstName") ?: ""
                    val last = doc.getString("lastName") ?: ""
                    val major = doc.getString("major") ?: ""
                    val pfp = doc.getString("profilePic") ?: ""

                    val friend = Friend(
                        friendId = friendId,
                        friendName = "$first $last",
                        friendMajor = major,
                        friendProfilePic = pfp
                    )

                    friendsList.add(friend)
                }

                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Log.e("MessagingFragment", "Error loading friends: ${it.message}")
            }
    }
}
