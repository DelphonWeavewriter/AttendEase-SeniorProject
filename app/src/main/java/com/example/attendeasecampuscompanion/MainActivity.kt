package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    // Added Firebase Auth
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialization
        auth = FirebaseAuth.getInstance()

        setContentView(R.layout.signin_layout)
    }

    override fun onStart() {
        super.onStart()
        // Check if user is already signed in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Navigation to the home screen if logged in
            Toast.makeText(this, "Already signed in as ${currentUser.email}", Toast.LENGTH_SHORT).show()
        }
    }

    fun signin(view: View) {
        val email = findViewById<EditText>(R.id.editTextEmailAddress).text.toString().trim()
        val password = findViewById<EditText>(R.id.editTextPassword).text.toString()

        Toast.makeText(this, "Attempting login with: $email", Toast.LENGTH_SHORT).show()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Toast.makeText(this, "LOGIN SUCCESSFUL - Welcome ${user?.email}", Toast.LENGTH_LONG).show()

                    // Navigate to home screen
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()

                } else {
                    val exactError = task.exception?.message ?: "Unknown error"
                    Toast.makeText(this, "EXACT ERROR: $exactError", Toast.LENGTH_LONG).show()
                    android.util.Log.e("FirebaseAuth", "Sign in failed", task.exception)
                }
            }
    }

    // Sign out function -> Maybe add it to each part of the app for quick exit?
    fun signOut() {
        auth.signOut()
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
    }
}