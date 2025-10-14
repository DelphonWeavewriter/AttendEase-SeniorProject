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
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var editText: EditText
    private lateinit var statusText: TextView
    private var attendanceID  = "CSCI456|Test"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_sender)

        editText = findViewById(R.id.editText)
        statusText = findViewById(R.id.statusText)
        val setButton: Button = findViewById(R.id.sendButton)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            statusText.text = "NFC not available"
            setButton.isEnabled = false
        } else {
            statusText.text = "NFC Tag Emulator Active\nPreset: \"$attendanceID\""
        }

        setButton.setOnClickListener {
            val newText = editText.text.toString()
            if (newText.isEmpty()) {
                Toast.makeText(this, "Enter preset text", Toast.LENGTH_SHORT).show()
            } else {
                attendanceID = newText
                statusText.text = "Preset Updated!\n\"$attendanceID\"\n\nReady to be scanned..."
                Toast.makeText(this, "Preset string updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            it.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // If another phone scans this phone's NFC, provide the attendance id
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            statusText.text = "Tag scanned!\nSending: \"$attendanceID\""
        }
    }

}