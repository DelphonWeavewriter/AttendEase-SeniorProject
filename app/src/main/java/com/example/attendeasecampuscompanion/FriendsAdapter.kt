package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.attendeasecampuscompanion.Friend
import com.example.attendeasecampuscompanion.R
import de.hdodenhof.circleimageview.CircleImageView

class FriendsAdapter(
    private val friends: List<Friend>,
    private val onMessageClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: CircleImageView = view.findViewById(R.id.friendProfileImage)
        val nameText: TextView = view.findViewById(R.id.friendNameText)
        val majorText: TextView = view.findViewById(R.id.friendMajorText)
        val messageButton: ImageButton = view.findViewById(R.id.messageButton)

        fun bind(friend: Friend) {
            nameText.text = friend.friendName
            majorText.text = friend.friendMajor

            // Load profile picture if exists
            if (!friend.friendProfilePic.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(friend.friendProfilePic)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.ic_profile_placeholder)
            }

            // Handle clicking the message button
            messageButton.setOnClickListener {
                onMessageClick(friend)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(friends[position])
    }

    override fun getItemCount(): Int = friends.size
}
