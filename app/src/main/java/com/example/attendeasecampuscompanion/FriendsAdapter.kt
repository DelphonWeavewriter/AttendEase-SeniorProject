package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView

class FriendsAdapter(
    private val friends: List<Friend>,
    private val onItemClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: CircleImageView = view.findViewById(R.id.friendProfileImage)
        val nameText: TextView = view.findViewById(R.id.friendNameText)
        val majorText: TextView = view.findViewById(R.id.friendMajorText)

        fun bind(friend: Friend) {
            nameText.text = friend.friendName
            majorText.text = friend.friendMajor

            itemView.setOnClickListener {
                onItemClick(friend)
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

    override fun getItemCount() = friends.size
}