package com.example.attendeasecampuscompanion.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.attendeasecampuscompanion.R
import com.example.attendeasecampuscompanion.ChatMessage

class ChatAdapter(
    private val currentUserId: String,
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val MSG_LEFT = 0
    private val MSG_RIGHT = 1

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) MSG_RIGHT else MSG_LEFT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == MSG_RIGHT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_right, parent, false)
            RightViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_left, parent, false)
            LeftViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is RightViewHolder) holder.message.text = msg.message
        if (holder is LeftViewHolder) holder.message.text = msg.message
    }

    override fun getItemCount(): Int = messages.size

    inner class RightViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val message: TextView = view.findViewById(R.id.messageTextRight)
    }

    inner class LeftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val message: TextView = view.findViewById(R.id.messageTextLeft)
    }
}
