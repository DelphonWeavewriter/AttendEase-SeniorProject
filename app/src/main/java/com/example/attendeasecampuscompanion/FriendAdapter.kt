package com.example.attendeasecampuscompanion.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.attendeasecampuscompanion.FriendMSG
import com.example.attendeasecampuscompanion.R

class FriendAdapter(
    private val friends: List<FriendMSG>,
    private val onSwitchToggle: (FriendMSG, Boolean) -> Unit,
    private val onButtonClick: (FriendMSG) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.friendName)
        val switch: Switch = view.findViewById(R.id.friendSwitch)
        val status: TextView = view.findViewById(R.id.switchStatus)
        val button: ImageButton = view.findViewById(R.id.friendButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.friend_list_item, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]

        holder.name.text = friend.fullName
        holder.status.text = "OFF"

        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            holder.status.text = if (isChecked) "ON" else "OFF"
            onSwitchToggle(friend, isChecked)
        }

        holder.button.setOnClickListener {
            onButtonClick(friend)
        }
    }

    override fun getItemCount(): Int = friends.size
}
