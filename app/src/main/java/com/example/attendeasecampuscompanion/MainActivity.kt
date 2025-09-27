package com.example.attendeasecampuscompanion

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.attendeasecampuscompanion.ui.theme.AttendEaseCampusCompanionTheme
import android.widget.Button
import android.widget.EditText
import android.widget.Toast


class MainActivity : ComponentActivity() {

    lateinit var username : EditText
    lateinit var password : EditText
    lateinit var signInButton : Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signin_layout)

//        enableEdgeToEdge()

//        setContent {
//            AttendEaseCampusCompanionTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }
//            }
//        }
    }

    fun signin(view: View) {
        val username = findViewById<EditText>(R.id.editTextEmailAddress).text.toString()
        val password = findViewById<EditText>(R.id.editTextPassword).text.toString()

        if (username == "test@nyit.edu" && password == "password")
        {
            Toast.makeText(this, "LOGIN SUCCESSFUL", Toast.LENGTH_SHORT).show()
        }
        else
        {
            Toast.makeText(this, "LOGIN UNSUCCESSFUL", Toast.LENGTH_SHORT).show()
        }
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AttendEaseCampusCompanionTheme {
        Greeting("Android")
    }
}