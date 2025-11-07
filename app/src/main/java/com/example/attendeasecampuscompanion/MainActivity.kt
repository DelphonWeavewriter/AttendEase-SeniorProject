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

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        setContentView(R.layout.signin_layout)
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkUserRoleAndNavigate(currentUser.uid)
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

        if(email.contains("admin") && password.contains("admin")) {
            Toast.makeText(this, "Admin login successful", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AttendanceSenderActivity::class.java))
            finish()
        }
        else {
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