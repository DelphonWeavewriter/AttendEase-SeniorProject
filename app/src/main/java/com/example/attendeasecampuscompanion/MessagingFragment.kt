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
import com.example.attendeasecampuscompanion.FriendsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MessagingFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var friendsAdapter: FriendsAdapter
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
        recyclerView = view.findViewById(R.id.friendsRecycler)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        friendsAdapter = FriendsAdapter(friendsList) { friend ->
            openChat(friend)
        }

        recyclerView.adapter = friendsAdapter

        loadFriends()
    }

    private fun loadFriends() {
        val currentUser = auth.currentUser ?: return

        db.collection("Users").document(currentUser.uid)
            .collection("Friends")
            .get()
            .addOnSuccessListener { snap ->
                friendsList.clear()

                for (doc in snap.documents) {
                    val friend = doc.toObject(Friend::class.java)
                    if (friend != null) {
                        friendsList.add(friend)
                    }
                }

                friendsAdapter.notifyDataSetChanged()
            }
    }

    // ------------------------------------------------------------
    // NEW FIXED FUNCTION — This replaces openMessageActivity
    // ------------------------------------------------------------
    private fun openChat(friend: Friend) {
        val currentUserId = auth.uid!!
        val friendId = friend.friendId

        val chatsRef = db.collection("Chats")

        // Search for an existing chat
        chatsRef.whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { query ->
                val existingChat = query.documents.find { doc ->
                    val participants = doc.get("participants") as? List<*>
                    participants?.contains(friendId) == true
                }

                if (existingChat != null) {
                    Log.e("CHAT", "Existing chat found: ${existingChat.id}")
                    launchMessageActivity(existingChat.id, friend)
                } else {
                    createChat(friend)
                }
            }
    }

    private fun createChat(friend: Friend) {
        val currentUserId = auth.uid!!
        val chatDoc = db.collection("Chats").document()

        val chatData = mapOf(
            "chatId" to chatDoc.id,
            "participants" to listOf(currentUserId, friend.friendId),
            "participantNames" to mapOf(
                currentUserId to "You",
                friend.friendId to friend.friendName
            ),
            "participantProfilePics" to mapOf(
                currentUserId to "",
                friend.friendId to friend.friendProfilePic
            ),
            "lastMessage" to "",
            "lastMessageTimestamp" to 0L
        )

        chatDoc.set(chatData).addOnSuccessListener {
            launchMessageActivity(chatDoc.id, friend)
        }
    }

    // ------------------------------------------------------------
    // Launch MessageActivity (this replaces openMessageActivity)
    // ------------------------------------------------------------
    private fun launchMessageActivity(chatId: String, friend: Friend) {
        val intent = Intent(requireContext(), MessageActivity::class.java)
        intent.putExtra("CHAT_ID", chatId)
        intent.putExtra("FRIEND_NAME", friend.friendName)
        startActivity(intent)
    }
}
