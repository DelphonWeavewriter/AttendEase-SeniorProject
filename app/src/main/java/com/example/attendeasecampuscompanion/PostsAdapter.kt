package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PostsAdapter(
    private val posts: List<Post>,
    private val currentUserId: String,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit,
    private val onAddFriendClick: (Post) -> Unit,
    private val onDeleteClick: (Post) -> Unit
) : RecyclerView.Adapter<PostsAdapter.PostViewHolder>() {

    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userImage: CircleImageView = view.findViewById(R.id.postUserImage)
        val userName: TextView = view.findViewById(R.id.postUserName)
        val userMajor: TextView = view.findViewById(R.id.postUserMajor)
        val content: TextView = view.findViewById(R.id.postContent)
        val likeButton: LinearLayout = view.findViewById(R.id.likeButton)
        val likeIcon: ImageView = view.findViewById(R.id.likeIcon)
        val likeCount: TextView = view.findViewById(R.id.likeCount)
        val commentButton: LinearLayout = view.findViewById(R.id.commentButton)
        val commentCount: TextView = view.findViewById(R.id.commentCount)
        val addFriendButton: ImageButton = view.findViewById(R.id.addFriendButton)
        val deletePostButton: ImageButton = view.findViewById(R.id.deletePostButton)

        fun bind(post: Post) {
            userName.text = post.userName
            userMajor.text = "${post.userMajor} • ${getTimeAgo(post.timestamp)}"
            content.text = post.content
            likeCount.text = post.likeCount.toString()
            commentCount.text = post.commentCount.toString()

            if (post.likes.contains(currentUserId)) {
                likeIcon.setImageResource(android.R.drawable.star_big_on)
            } else {
                likeIcon.setImageResource(android.R.drawable.star_big_off)
            }

            if (post.userId == currentUserId) {
                addFriendButton.visibility = View.GONE
                deletePostButton.visibility = View.VISIBLE
            } else {
                addFriendButton.visibility = View.VISIBLE
                deletePostButton.visibility = View.GONE
            }

            likeButton.setOnClickListener {
                onLikeClick(post)
            }

            commentButton.setOnClickListener {
                onCommentClick(post)
            }

            addFriendButton.setOnClickListener {
                onAddFriendClick(post)
            }

            deletePostButton.setOnClickListener {
                onDeleteClick(post)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount() = posts.size

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}