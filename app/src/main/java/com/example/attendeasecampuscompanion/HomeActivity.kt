package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()

        // Getter
        val user = auth.currentUser

        // Setter
        findViewById<TextView>(R.id.studentName).text = user?.email?.substringBefore("@") ?: "Student"
        findViewById<TextView>(R.id.studentId).text = "ID: Coming soon..."
        findViewById<TextView>(R.id.nextClass).text = "Next Class: Coming soon..."

        // NFC Check In button
        findViewById<Button>(R.id.btnCheckIn).setOnClickListener {
            Toast.makeText(this, "Logan", Toast.LENGTH_SHORT).show()
            // TODO: Implement NFC scanning functionality
        }

        // Homescreen buttons
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            Toast.makeText(this, "Steven", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSocial).setOnClickListener {
            Toast.makeText(this, "Sam", Toast.LENGTH_SHORT).show()
            // TODO: Implement social media for campuslife
        }

        findViewById<Button>(R.id.btnCampusMap).setOnClickListener {
            Toast.makeText(this, "Rushil", Toast.LENGTH_SHORT).show()
            // TODO: Implement the 3D map
        }

        findViewById<Button>(R.id.btnFinalsSchedule).setOnClickListener {
            Toast.makeText(this, "Bram", Toast.LENGTH_SHORT).show()
            // TODO: Need the app to read course finals and link with student registered classes
        }

        findViewById<Button>(R.id.btnSignOut).setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}