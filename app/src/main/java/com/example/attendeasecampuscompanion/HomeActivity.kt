package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val user = auth.currentUser

        user?.uid?.let { uid ->
            fetchUserData(uid)
        }

        findViewById<Button>(R.id.btnCheckIn).setOnClickListener {
            Toast.makeText(this, "Starting NFC Scanning...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AttendanceActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            Toast.makeText(this, "Opening Your Schedule...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSocial).setOnClickListener {
            Toast.makeText(this, "Opening SocialEaze...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnCampusMap).setOnClickListener {
            Toast.makeText(this, "Opening Your Campus Map...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, com.example.attendeasecampuscompanion.map.MapActivity::class.java))
        }

        findViewById<Button>(R.id.btnFinalsSchedule).setOnClickListener {
            Toast.makeText(this, "Checking Your Finals Schedule...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSignOut).setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun fetchUserData(userId: String) {
        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userData = document.toObject(User::class.java)

                    userData?.let {
                        findViewById<TextView>(R.id.studentName).text = "${it.firstName} ${it.lastName}"
                        findViewById<TextView>(R.id.studentId).text = "ID: ${it.userId}"
                        findViewById<TextView>(R.id.nextClass).text = "Next Class: Coming soon..."

                        val dateHeader = findViewById<TextView>(R.id.dateHeader)
                        val formatter = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault())
                        val currentDate = formatter.format(java.util.Date())
                        dateHeader.text = currentDate
                    }
                }
                else {
                    findViewById<TextView>(R.id.studentName).text = auth.currentUser?.email?.substringBefore("@") ?: "Student"
                    findViewById<TextView>(R.id.studentId).text = "ID: Not found"
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Error fetching user data", e)
                Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
                findViewById<TextView>(R.id.studentName).text = auth.currentUser?.email?.substringBefore("@") ?: "Student"
            }
    }
}