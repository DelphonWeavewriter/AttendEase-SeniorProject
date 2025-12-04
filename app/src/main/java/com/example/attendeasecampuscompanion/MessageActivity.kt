package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendeasecampuscompanion.adapters.ChatAdapter
import com.example.attendeasecampuscompanion.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MessageActivity : ComponentActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = ArrayList<ChatMessage>()

    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var currentUserId: String
    private lateinit var friendUid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        currentUserId = auth.currentUser!!.uid
        friendUid = intent.getStringExtra("FRIEND_UID")!!

        chatRecycler = findViewById(R.id.chatRecycler)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        adapter = ChatAdapter(currentUserId, messages)
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatRecycler.adapter = adapter

        loadMessages()

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text)
        }
    }

    private fun sendMessage(text: String) {
        val msg = ChatMessage(
            senderId = currentUserId,
            receiverId = friendUid,
            message = text,
            timestamp = System.currentTimeMillis()
        )

        val senderPath = db.collection("Users").document(currentUserId)
            .collection("Messages").document(friendUid)
            .collection("Messages")

        val receiverPath = db.collection("Users").document(friendUid)
            .collection("Messages").document(currentUserId)
            .collection("Messages")

        senderPath.add(msg)
        receiverPath.add(msg)

        messageInput.setText("")
    }

    private fun loadMessages() {
        db.collection("Users").document(currentUserId)
            .collection("Messages").document(friendUid)
            .collection("Messages")
            .orderBy("timestamp")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    messages.clear()
                    for (doc in snap.documents) {
                        messages.add(doc.toObject(ChatMessage::class.java)!!)
                    }
                    adapter.notifyDataSetChanged()
                    chatRecycler.scrollToPosition(messages.size - 1)
                }
            }
    }
}
