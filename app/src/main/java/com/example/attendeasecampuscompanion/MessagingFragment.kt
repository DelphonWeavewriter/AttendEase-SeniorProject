package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendeasecampuscompanion.adapters.FriendAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MessagingFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FriendAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val friendsArray = ArrayList<FriendMSG>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messaging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        recyclerView = view.findViewById(R.id.messagingRecycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Create Adapter
        adapter = FriendAdapter(
            friendsArray,
            onSwitchToggle = { _, _ -> /* no switch here */ },
            onButtonClick = { friend ->
                val intent = Intent(requireContext(), MessageActivity::class.java)
                intent.putExtra("FRIEND_UID", friend.uid)
                intent.putExtra("FRIEND_NAME", friend.fullName)
                startActivity(intent)
            }
        )

        recyclerView.adapter = adapter

        // Load Friends
        loadFriends()
    }

    private fun loadFriends() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users")
            .document(currentUserId)
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
}
