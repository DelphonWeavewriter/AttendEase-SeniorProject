package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        setContentView(R.layout.signin_layout)
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            //checkUserRoleAndNavigate(currentUser.uid)
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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
                    user?.uid?.let { uid ->
                        checkUserRoleAndNavigate(uid)
                    }
                } else {
                    val exactError = task.exception?.message ?: "Unknown error"
                    Toast.makeText(this, "EXACT ERROR: $exactError", Toast.LENGTH_LONG).show()
                    android.util.Log.e("FirebaseAuth", "Sign in failed", task.exception)
                }
            }

    }

    fun forgotPassword(view: View) {
        val emailInput = EditText(this)
        emailInput.hint = "Enter your email"
        emailInput.setPadding(50, 20, 50, 20)

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("Enter your email address to receive a password reset link")
            .setView(emailInput)
            .setPositiveButton("Send") { _, _ ->
                val email = emailInput.text.toString().trim()

                if (email.isEmpty()) {
                    Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "Password reset email sent to $email",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Failed to send reset email: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkUserRoleAndNavigate(userId: String) {
        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userData = document.toObject(User::class.java)
                    val firstName = userData?.firstName ?: "User"
                    val role = userData?.role ?: "Student"

                    Toast.makeText(this, "Welcome back, $firstName!", Toast.LENGTH_SHORT).show()

                    val intent = if (role == "Professor") {
                        Intent(this, ProfessorHomeActivity::class.java)
                    } else if (role == "Admin") {
                        Intent(this, AttendanceSenderActivity::class.java)
                    } else {
                        Intent(this, HomeActivity::class.java)
                    }

                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Error fetching user", e)
                Toast.makeText(this, "Error checking user role", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
    }

    fun signOut() {
        auth.signOut()
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
    }
}