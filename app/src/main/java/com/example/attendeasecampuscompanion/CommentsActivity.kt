package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class CommentsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: TextView
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentInput: EditText
    private lateinit var sendCommentButton: ImageButton
    private lateinit var emptyText: TextView

    private lateinit var adapter: CommentsAdapter
    private val commentsList = mutableListOf<Comment>()

    private var postId: String = ""
    private var postUserId: String = ""
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        postId = intent.getStringExtra("postId") ?: ""
        postUserId = intent.getStringExtra("postUserId") ?: ""

        backButton = findViewById(R.id.backButton)
        commentsRecyclerView = findViewById(R.id.commentsRecyclerView)
        commentInput = findViewById(R.id.commentInput)
        sendCommentButton = findViewById(R.id.sendCommentButton)
        emptyText = findViewById(R.id.emptyText)

        loadCurrentUser()
        setupRecyclerView()
        loadComments()

        backButton.setOnClickListener {
            finish()
        }

        sendCommentButton.setOnClickListener {
            addComment()
        }
    }

    private fun loadCurrentUser() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                currentUser = document.toObject(User::class.java)
            }
    }

    private fun setupRecyclerView() {
        adapter = CommentsAdapter(commentsList)
        commentsRecyclerView.layoutManager = LinearLayoutManager(this)
        commentsRecyclerView.adapter = adapter
    }

    private fun loadComments() {
        db.collection("Posts").document(postId).collection("Comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                commentsList.clear()
                for (doc in documents) {
                    val comment = doc.toObject(Comment::class.java)
                    commentsList.add(comment)
                }
                adapter.notifyDataSetChanged()

                if (commentsList.isEmpty()) {
                    emptyText.visibility = android.view.View.VISIBLE
                    commentsRecyclerView.visibility = android.view.View.GONE
                } else {
                    emptyText.visibility = android.view.View.GONE
                    commentsRecyclerView.visibility = android.view.View.VISIBLE
                }
            }
    }

    private fun addComment() {
        val content = commentInput.text.toString().trim()

        if (content.isEmpty()) {
            Toast.makeText(this, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val user = currentUser ?: return
        val commentId = db.collection("Posts").document(postId).collection("Comments").document().id

        val comment = Comment(
            commentId = commentId,
            postId = postId,
            userId = auth.currentUser?.uid ?: "",
            userName = "${user.firstName} ${user.lastName}",
            userProfilePic = user.profilePictureUrl,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        db.collection("Posts").document(postId).collection("Comments")
            .document(commentId)
            .set(comment)
            .addOnSuccessListener {
                updateCommentCount()
                sendCommentNotification()
                commentInput.text.clear()
                loadComments()
                Toast.makeText(this, "Comment added", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCommentCount() {
        db.collection("Posts").document(postId).collection("Comments")
            .get()
            .addOnSuccessListener { documents ->
                db.collection("Posts").document(postId)
                    .update("commentCount", documents.size())
            }
    }

    private fun sendCommentNotification() {
        if (postUserId == auth.currentUser?.uid) return

        val user = currentUser ?: return
        val notifId = db.collection("Users").document(postUserId).collection("Notifications").document().id

        val notification = Notification(
            notificationId = notifId,
            type = "COMMENT",
            fromUserId = auth.currentUser?.uid ?: "",
            fromUserName = "${user.firstName} ${user.lastName}",
            fromUserProfilePic = user.profilePictureUrl,
            postId = postId,
            message = "${user.firstName} ${user.lastName} commented on your post",
            timestamp = System.currentTimeMillis(),
            read = false
        )

        db.collection("Users").document(postUserId).collection("Notifications")
            .document(notifId)
            .set(notification)
    }
}