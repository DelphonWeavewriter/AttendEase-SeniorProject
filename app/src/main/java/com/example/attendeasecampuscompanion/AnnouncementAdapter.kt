package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class AnnouncementAdapter : RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder>() {

    private var announcements = listOf<Announcement>()

    fun submitList(newList: List<Announcement>) {
        announcements = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement, parent, false)
        return AnnouncementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnnouncementViewHolder, position: Int) {
        holder.bind(announcements[position])
    }

    override fun getItemCount() = announcements.size

    class AnnouncementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.cardAnnouncement)
        private val message: TextView = itemView.findViewById(R.id.tvAnnouncementMessage)
        private val timestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val professor: TextView = itemView.findViewById(R.id.tvProfessor)
        private val priorityIndicator: View = itemView.findViewById(R.id.viewPriorityIndicator)

        fun bind(announcement: Announcement) {
            message.text = announcement.message
            timestamp.text = announcement.getTimeAgo()
            professor.text = "Posted by ${announcement.createdByName}"

            val color = when (announcement.priority) {
                "URGENT" -> {
                    ContextCompat.getColor(itemView.context, R.color.error_red)
                }
                else -> {
                    ContextCompat.getColor(itemView.context, R.color.primary_blue)
                }
            }
            priorityIndicator.setBackgroundColor(color)

            if (announcement.priority == "URGENT") {
                card.strokeWidth = 4
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.error_red)
            } else {
                card.strokeWidth = 0
            }
        }
    }
}