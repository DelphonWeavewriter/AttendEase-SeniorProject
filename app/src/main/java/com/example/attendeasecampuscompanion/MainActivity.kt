package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialization
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        setContentView(R.layout.signin_layout)
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Get user's first name before showing toast
            fetchUserName(currentUser.uid) { firstName ->
                Toast.makeText(this, "Already signed in as $firstName", Toast.LENGTH_SHORT).show()
            }
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

                    // Fetch user data from Firestore
                    user?.uid?.let { uid ->
                        fetchUserName(uid) { firstName ->
                            Toast.makeText(this, "Success - Welcome $firstName!", Toast.LENGTH_LONG).show()

                            // Navigate to home screen
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        }
                    }
                } else {
                    val exactError = task.exception?.message ?: "Unknown error"
                    Toast.makeText(this, "EXACT ERROR: $exactError", Toast.LENGTH_LONG).show()
                    android.util.Log.e("FirebaseAuth", "Sign in failed", task.exception)
                }
            }
    }

    private fun fetchUserName(userId: String, callback: (String) -> Unit) {
        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val firstName = document.getString("firstName") ?: "Student"
                    callback(firstName)
                } else {
                    callback("Student")
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Error fetching user", e)
                callback("Student")
            }
    }

    fun signOut() {
        auth.signOut()
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
    }
}