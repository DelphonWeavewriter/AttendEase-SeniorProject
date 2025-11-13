package com.example.attendeasecampuscompanion

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class AttendanceSenderActivity : AppCompatActivity() {
    private lateinit var editText: EditText
    private lateinit var statusText: TextView
    private lateinit var roomSpinner: Spinner
    private var nfcAdapter: NfcAdapter? = null
    private var cardEmulation: CardEmulation? = null
    private var selectedRoom = "235"
    private val db = FirebaseFirestore.getInstance() // Add this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_sender)

        editText = findViewById(R.id.editText)
        statusText = findViewById(R.id.statusText)
        val setButton: Button = findViewById(R.id.sendButton)
        roomSpinner = findViewById(R.id.roomSpinner)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_SHORT).show()
            setButton.isEnabled = false
            return
        }

        cardEmulation = CardEmulation.getInstance(nfcAdapter)

        if (!packageManager.hasSystemFeature("android.hardware.nfc.hce")) {
            statusText.text = "HCE not supported on this device"
            setButton.isEnabled = false
            return
        }

        // Load rooms from Firebase (with fallback)
        loadRoomsFromFirebase()

        editText.setText(HCEService.dataToSend)
        statusText.text = "Ready to be scanned...\nRoom: $selectedRoom"

        setButton.setOnClickListener {
            if (selectedRoom.isEmpty()) {
                Toast.makeText(this, "Select a room", Toast.LENGTH_SHORT).show()
            } else {
                HCEService.dataToSend = selectedRoom
                statusText.text = "Ready to scan for room: $selectedRoom\n\nHold near student's phone..."
                Toast.makeText(this, "Broadcasting room: $selectedRoom", Toast.LENGTH_SHORT).show()
            }
        }

        val component = ComponentName(this, HCEService::class.java)
        if (!cardEmulation!!.isDefaultServiceForCategory(component, CardEmulation.CATEGORY_OTHER)) {
            Toast.makeText(
                this,
                "Please set this app as default for NFC in settings",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadRoomsFromFirebase() {
        db.collection("Rooms")
            .get()
            .addOnSuccessListener { documents ->
                val rooms = documents.mapNotNull { it.getString("roomNumber") }
                    .filter { it.isNotEmpty() }
                    .sorted() // Sort rooms alphabetically

                if (rooms.isNotEmpty()) {
                    setupRoomSpinner(rooms)
                } else {
                    // Fallback to hardcoded rooms
                    setupRoomSpinner(listOf("235", "240", "301", "302"))
                    Toast.makeText(this, "Using default room list", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                // Use default rooms on error
                setupRoomSpinner(listOf("235", "240", "301", "302"))
                Toast.makeText(this, "Error loading rooms: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRoomSpinner(rooms: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rooms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roomSpinner.adapter = adapter

        roomSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRoom = rooms[position]
                statusText.text = "Ready to be scanned...\nRoom: $selectedRoom"
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedRoom = if (rooms.isNotEmpty()) rooms[0] else "235"
            }
        }

        // Set initial selection
        if (rooms.isNotEmpty()) {
            selectedRoom = rooms[0]
        }
    }

    override fun onResume() {
        super.onResume()
        statusText.text = "Ready to be scanned...\nRoom: $selectedRoom"
    }
}