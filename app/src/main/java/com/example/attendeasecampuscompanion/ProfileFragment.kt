package com.example.attendeasecampuscompanion

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView
import com.bumptech.glide.Glide
import java.util.*

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var profileImage: CircleImageView
    private lateinit var nameText: TextView
    private lateinit var emailText: TextView
    private lateinit var majorText: TextView
    private lateinit var bioText: TextView
    private lateinit var friendCountText: TextView
    private lateinit var editBioButton: ImageButton
    private lateinit var editProfilePicButton: ImageButton
    private lateinit var viewFriendsButton: Button
    private lateinit var viewRequestsButton: Button
    private lateinit var notificationSwitch: Switch
    private lateinit var locationSwitch: Switch
    private lateinit var privacySpinner: Spinner
    private lateinit var signOutButton: Button
    private lateinit var backButton: TextView

    private var currentUser: User? = null
    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uploadProfilePicture(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        backButton = view.findViewById(R.id.backButton)
        profileImage = view.findViewById(R.id.profileImage)
        nameText = view.findViewById(R.id.nameText)
        emailText = view.findViewById(R.id.emailText)
        majorText = view.findViewById(R.id.majorText)
        bioText = view.findViewById(R.id.bioText)
        friendCountText = view.findViewById(R.id.friendCountText)
        editBioButton = view.findViewById(R.id.editBioButton)
        editProfilePicButton = view.findViewById(R.id.editProfilePicButton)
        viewFriendsButton = view.findViewById(R.id.viewFriendsButton)
        viewRequestsButton = view.findViewById(R.id.viewRequestsButton)
        notificationSwitch = view.findViewById(R.id.notificationSwitch)
        locationSwitch = view.findViewById(R.id.locationSwitch)
        privacySpinner = view.findViewById(R.id.privacySpinner)
        signOutButton = view.findViewById(R.id.signOutButton)

        setupPrivacySpinner()
        loadUserProfile()
        loadFriendCount()

        backButton.setOnClickListener {
            requireActivity().finish()
        }

        editProfilePicButton.setOnClickListener {
            openImagePicker()
        }

        editBioButton.setOnClickListener {
            showEditBioDialog()
        }

        viewFriendsButton.setOnClickListener {
            startActivity(Intent(requireContext(), FriendsListActivity::class.java))
        }

        viewRequestsButton.setOnClickListener {
            startActivity(Intent(requireContext(), FriendRequestsActivity::class.java))
        }

        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateSetting("notificationsEnabled", isChecked)
        }

        locationSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateSetting("locationSharingEnabled", isChecked)
        }

        signOutButton.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun setupPrivacySpinner() {
        val privacyOptions = arrayOf("Public", "Friends Only", "Private")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, privacyOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        privacySpinner.adapter = adapter

        privacySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val privacy = when (position) {
                    0 -> "public"
                    1 -> "friends"
                    2 -> "private"
                    else -> "public"
                }
                updateSetting("profilePrivacy", privacy)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentUser = document.toObject(User::class.java)
                    currentUser?.let { user ->
                        nameText.text = "${user.firstName} ${user.lastName}"
                        emailText.text = user.email
                        majorText.text = user.major
                        bioText.text = if (user.bio.isEmpty()) "No bio yet" else user.bio

                        if (user.profilePictureUrl.isNotEmpty()) {
                            Glide.with(requireContext())
                                .load(user.profilePictureUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .into(profileImage)
                        }

                        val settings = user.settings
                        notificationSwitch.isChecked = settings["notificationsEnabled"] as? Boolean ?: true
                        locationSwitch.isChecked = settings["locationSharingEnabled"] as? Boolean ?: false

                        val privacy = settings["profilePrivacy"] as? String ?: "public"
                        privacySpinner.setSelection(
                            when (privacy) {
                                "public" -> 0
                                "friends" -> 1
                                "private" -> 2
                                else -> 0
                            }
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadFriendCount() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).collection("Friends")
            .get()
            .addOnSuccessListener { documents ->
                friendCountText.text = "${documents.size()} Friends"
            }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun uploadProfilePicture(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_pictures/$userId.jpg")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    db.collection("Users").document(userId)
                        .update("profilePictureUrl", downloadUri.toString())
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Profile picture updated", Toast.LENGTH_SHORT).show()
                            loadUserProfile()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditBioDialog() {
        val input = EditText(requireContext())
        input.setText(currentUser?.bio ?: "")

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Bio")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newBio = input.text.toString()
                updateBio(newBio)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateBio(bio: String) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId)
            .update("bio", bio)
            .addOnSuccessListener {
                bioText.text = if (bio.isEmpty()) "No bio yet" else bio
                Toast.makeText(requireContext(), "Bio updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update bio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSetting(key: String, value: Any) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId)
            .update("settings.$key", value)
            .addOnSuccessListener {
                when (key) {
                    "locationSharingEnabled" -> {
                        if (value as Boolean) {
                            Toast.makeText(requireContext(), "Location sharing enabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update setting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}