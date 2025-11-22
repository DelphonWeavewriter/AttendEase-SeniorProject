package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import de.hdodenhof.circleimageview.CircleImageView

class ExploreFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: TextView
    private lateinit var notificationButton: ImageButton
    private lateinit var profileAvatar: CircleImageView
    private lateinit var postInput: EditText
    private lateinit var sendPostButton: ImageButton
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var emptyText: TextView

    private lateinit var adapter: PostsAdapter
    private val postsList = mutableListOf<Post>()

    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_explore, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        backButton = view.findViewById(R.id.backButton)
        notificationButton = view.findViewById(R.id.notificationButton)
        profileAvatar = view.findViewById(R.id.profileAvatar)
        postInput = view.findViewById(R.id.postInput)
        sendPostButton = view.findViewById(R.id.sendPostButton)
        postsRecyclerView = view.findViewById(R.id.postsRecyclerView)
        emptyText = view.findViewById(R.id.emptyText)

        loadCurrentUser()
        setupRecyclerView()
        loadPosts()

        backButton.setOnClickListener {
            requireActivity().finish()
        }

        notificationButton.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationsActivity::class.java))
        }

        profileAvatar.setOnClickListener {
            (requireActivity() as? SocialActivity)?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_profile
        }

        sendPostButton.setOnClickListener {
            createPost()
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
        adapter = PostsAdapter(
            postsList,
            currentUserId = auth.currentUser?.uid ?: "",
            onLikeClick = { post -> toggleLike(post) },
            onCommentClick = { post -> openComments(post) },
            onAddFriendClick = { post -> sendFriendRequest(post) },
            onDeleteClick = { post -> deletePost(post) }
        )

        postsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        postsRecyclerView.adapter = adapter
    }

    private fun createPost() {
        val content = postInput.text.toString().trim()

        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "Post cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (content.length > 280) {
            Toast.makeText(requireContext(), "Post too long (max 280 characters)", Toast.LENGTH_SHORT).show()
            return
        }

        val user = currentUser ?: return
        val postId = db.collection("Posts").document().id

        val post = Post(
            postId = postId,
            userId = auth.currentUser?.uid ?: "",
            userName = "${user.firstName} ${user.lastName}",
            userProfilePic = user.profilePictureUrl,
            userMajor = user.major,
            content = content,
            timestamp = System.currentTimeMillis(),
            likes = emptyList(),
            likeCount = 0,
            commentCount = 0
        )

        db.collection("Posts").document(postId)
            .set(post)
            .addOnSuccessListener {
                postInput.text.clear()
                Toast.makeText(requireContext(), "Posted!", Toast.LENGTH_SHORT).show()
                loadPosts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error posting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadPosts() {
        db.collection("Posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                postsList.clear()
                for (doc in documents) {
                    val post = doc.toObject(Post::class.java)
                    postsList.add(post)
                }
                adapter.notifyDataSetChanged()

                if (postsList.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    postsRecyclerView.visibility = View.GONE
                } else {
                    emptyText.visibility = View.GONE
                    postsRecyclerView.visibility = View.VISIBLE
                }
            }
    }

    private fun toggleLike(post: Post) {
        val userId = auth.currentUser?.uid ?: return
        val currentLikes = post.likes.toMutableList()

        if (currentLikes.contains(userId)) {
            currentLikes.remove(userId)
        } else {
            currentLikes.add(userId)
            sendLikeNotification(post)
        }

        db.collection("Posts").document(post.postId)
            .update(
                mapOf(
                    "likes" to currentLikes,
                    "likeCount" to currentLikes.size
                )
            )
            .addOnSuccessListener {
                loadPosts()
            }
    }

    private fun sendLikeNotification(post: Post) {
        if (post.userId == auth.currentUser?.uid) return

        val user = currentUser ?: return
        val notifId = db.collection("Users").document(post.userId).collection("Notifications").document().id

        val notification = Notification(
            notificationId = notifId,
            type = "LIKE",
            fromUserId = auth.currentUser?.uid ?: "",
            fromUserName = "${user.firstName} ${user.lastName}",
            fromUserProfilePic = user.profilePictureUrl,
            postId = post.postId,
            message = "${user.firstName} ${user.lastName} liked your post",
            timestamp = System.currentTimeMillis(),
            read = false
        )

        db.collection("Users").document(post.userId).collection("Notifications")
            .document(notifId)
            .set(notification)
    }

    private fun openComments(post: Post) {
        val intent = Intent(requireContext(), CommentsActivity::class.java)
        intent.putExtra("postId", post.postId)
        intent.putExtra("postUserId", post.userId)
        startActivity(intent)
    }

    private fun sendFriendRequest(post: Post) {
        if (post.userId == auth.currentUser?.uid) {
            Toast.makeText(requireContext(), "Cannot add yourself", Toast.LENGTH_SHORT).show()
            return
        }

        val user = currentUser ?: return
        val requestId = db.collection("Users").document(post.userId).collection("FriendRequests").document().id

        val sentRequest = FriendRequest(
            requestId = requestId,
            fromUserId = auth.currentUser?.uid ?: "",
            toUserId = post.userId,
            fromUserName = "${user.firstName} ${user.lastName}",
            fromUserMajor = user.major,
            fromUserProfilePic = user.profilePictureUrl,
            status = "pending",
            timestamp = System.currentTimeMillis(),
            type = "sent"
        )

        val receivedRequest = sentRequest.copy(type = "received")

        db.collection("Users").document(auth.currentUser?.uid ?: "").collection("FriendRequests")
            .document(requestId)
            .set(sentRequest)

        db.collection("Users").document(post.userId).collection("FriendRequests")
            .document(requestId)
            .set(receivedRequest)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Friend request sent", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deletePost(post: Post) {
        db.collection("Posts").document(post.postId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Post deleted", Toast.LENGTH_SHORT).show()
                loadPosts()
            }
    }
}