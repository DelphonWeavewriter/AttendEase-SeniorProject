package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendeasecampuscompanion.adapters.ChatAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MessageActivity : ComponentActivity() {

    private lateinit var chatId: String
    private lateinit var friendName: String
    private lateinit var adapter: ChatAdapter

    private val messages = ArrayList<ChatMessage>()

    private lateinit var chatRecycler: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        friendName = intent.getStringExtra("FRIEND_NAME") ?: ""

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.chatTitle).text = friendName

        if (chatId.isEmpty()) {
            finish()
            return
        }

        chatRecycler = findViewById(R.id.chatRecycler)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        adapter = ChatAdapter(auth.uid!!, messages)
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
            senderId = auth.uid!!,
            message = text,
            timestamp = System.currentTimeMillis()
        )

        val msgRef = db.collection("Chats")
            .document(chatId)
            .collection("Messages")
            .document()

        msgRef.set(msg)

        // Update chat preview
        db.collection("Chats").document(chatId).update(
            mapOf(
                "lastMessage" to text,
                "lastMessageTimestamp" to msg.timestamp,
                "lastMessageSenderId" to auth.uid!!
            )
        )

        messageInput.setText("")
    }

    private fun loadMessages() {
        db.collection("Chats").document(chatId)
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
