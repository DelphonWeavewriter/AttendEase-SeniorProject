package com.example.attendeasecampuscompanion

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcEvent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.StandardCharsets

class AttendanceSenderActivity : AppCompatActivity(){
    private lateinit var editText: EditText
    private lateinit var statusText: TextView
    private var attendanceID  = "CSCI456|Test"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_sender)

        editText = findViewById(R.id.editText)
        statusText = findViewById(R.id.statusText)
        val setButton: Button = findViewById(R.id.sendButton)
        val shareButton: Button = findViewById(R.id.sendButton)


        setButton.setOnClickListener {
            val newText = editText.text.toString()
            if (newText.isEmpty()) {
                Toast.makeText(this, "Enter attendance ID", Toast.LENGTH_SHORT).show()
            } else {
                attendanceID = newText
                statusText.text = "Preset Updated!\n\"$attendanceID\"\n\nReady to be scanned..."
                Toast.makeText(this, "Preset string updated", Toast.LENGTH_SHORT).show()
            }
        }

        shareButton.setOnClickListener {
            if (attendanceID.isEmpty()) {
                Toast.makeText(this, "No string set", Toast.LENGTH_SHORT).show()
            } else {
                shareViaNearbySend(attendanceID)
            }
        }

        statusText.text = "Current attendance ID: "+attendanceID
    }

    private fun shareViaNearbySend(text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        // Create chooser to show Nearby Share option
        val shareIntent = Intent.createChooser(sendIntent, "Share via Nearby Share")

        try {
            startActivity(shareIntent)
            statusText.text = "Sharing: \"$text\""
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}