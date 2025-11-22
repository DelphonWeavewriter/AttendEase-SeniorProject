package com.example.attendeasecampuscompanion

data class FriendRequest(
    val requestId: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val fromUserName: String = "",
    val fromUserMajor: String = "",
    val fromUserProfilePic: String = "",
    val status: String = "pending",
    val timestamp: Long = 0L,
    val type: String = ""
)

data class Friend(
    val friendId: String = "",
    val friendName: String = "",
    val friendMajor: String = "",
    val friendProfilePic: String = "",
    val status: String = "active",
    val becameFriendsAt: Long = 0L
)

data class Chat(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantProfilePics: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = "",
    val unreadCount: Map<String, Int> = emptyMap()
)

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val read: Boolean = false
)

data class Post(
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfilePic: String = "",
    val userMajor: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val likes: List<String> = emptyList(),
    val likeCount: Int = 0,
    val commentCount: Int = 0
)

data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfilePic: String = "",
    val content: String = "",
    val timestamp: Long = 0L
)

data class Notification(
    val notificationId: String = "",
    val type: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val fromUserProfilePic: String = "",
    val postId: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val read: Boolean = false
)