package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class FriendRequestsAdapter(
    private val requests: List<FriendRequest>,
    private val onAccept: (FriendRequest) -> Unit,
    private val onDecline: (FriendRequest) -> Unit
) : RecyclerView.Adapter<FriendRequestsAdapter.RequestViewHolder>() {

    inner class RequestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: CircleImageView = view.findViewById(R.id.requestProfileImage)
        val nameText: TextView = view.findViewById(R.id.requestNameText)
        val majorText: TextView = view.findViewById(R.id.requestMajorText)
        val acceptButton: Button = view.findViewById(R.id.acceptButton)
        val declineButton: Button = view.findViewById(R.id.declineButton)

        fun bind(request: FriendRequest) {
            nameText.text = request.fromUserName
            majorText.text = request.fromUserMajor

            if (request.fromUserProfilePic.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(request.fromUserProfilePic)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(profileImage)
            }

            acceptButton.setOnClickListener {
                onAccept(request)
            }

            declineButton.setOnClickListener {
                onDecline(request)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount() = requests.size
}