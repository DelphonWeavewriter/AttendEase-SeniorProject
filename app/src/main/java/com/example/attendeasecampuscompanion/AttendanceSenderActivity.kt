package com.example.attendeasecampuscompanion

import android.util.Log
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
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.Ndef
import android.os.Build
import androidx.annotation.RequiresApi
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AttendanceSenderActivity : AppCompatActivity() {
    private lateinit var timeSet: EditText
    private lateinit var statusText: TextView
    private lateinit var roomSpinner: Spinner
    private lateinit var btnBack: TextView
    private var nfcAdapter: NfcAdapter? = null
    private var cardEmulation: CardEmulation? = null
    private var time: String = "11:45:00 AM"
    private var selectedRoom = "235"
    @RequiresApi(Build.VERSION_CODES.O)
    val currentDate: LocalDate = LocalDate.now()
    @RequiresApi(Build.VERSION_CODES.O)
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    @RequiresApi(Build.VERSION_CODES.O)
    val formattedDate: String = currentDate.format(formatter)
    private val db = FirebaseFirestore.getInstance()
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null

    private var nfcTagData: String = ""

    private var currentMode: NfcMode = NfcMode.NONE

    enum class NfcMode {
        NONE,
        READER,
        HCE
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_sender)

        timeSet = findViewById(R.id.timeSet)
        statusText = findViewById(R.id.statusText)
        val setButton: Button = findViewById(R.id.sendButton)
        roomSpinner = findViewById(R.id.roomSpinner)
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed()}


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


        statusText.text = "Ready to be scanned...\nRoom: $selectedRoom"

        setButton.setOnClickListener {
            if (selectedRoom.isEmpty()) {
                Toast.makeText(this, "Select a room", Toast.LENGTH_SHORT).show()
            } else {
                HCEService.dataToSend = selectedRoom
                statusText.text = "Ready to scan for room: $selectedRoom"
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

        val readerModeButton: Button = findViewById(R.id.readerModeButton)
        val hceModeButton: Button = findViewById(R.id.hceModeButton)

        readerModeButton.setOnClickListener {
            switchToReaderMode()
        }

        hceModeButton.setOnClickListener {
            switchToHceMode()
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

    override fun onPause() {
        super.onPause()

        when (currentMode) {
            NfcMode.READER -> nfcAdapter?.disableReaderMode(this)
            NfcMode.HCE -> { /* HCE continues in background */ }
            NfcMode.NONE -> { }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        setupNfcForCurrentMode()

//        statusText.text = "Ready to be scanned...\nRoom: $selectedRoom"
//
//        val callback = NfcAdapter.ReaderCallback { tag ->
//            onTagDiscovered(tag)
//        }
//
//        // Enable foreground dispatch to intercept NFC intents
//        nfcAdapter?.enableReaderMode(
//            this,
//            callback,
//            NfcAdapter.FLAG_READER_NFC_A or
//                    NfcAdapter.FLAG_READER_NFC_B,
//            null
//        )
    }


        @RequiresApi(Build.VERSION_CODES.O)
        override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleNfcIntent(intent)

        // Check if the intent contains NFC tag data
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                readNfcTag(it)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        val nfcA = android.nfc.tech.NfcA.get(tag)
        if (nfcA == null) {
            Log.d("AttendanceSender", "Tag is null")
            return
        }

        Log.d("AttendnaceSender", "Tag detected: ${tag.id.contentToString()}")

        readNfcTag(tag)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleNfcIntent(intent: Intent) {
        if (intent == null) return

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (rawMessages != null && rawMessages.isNotEmpty()) {
                val ndefMessage = rawMessages[0] as NdefMessage
                val text = parseNdefMessage(ndefMessage)
                if (text.isNotEmpty()) {
                    nfcTagData = text
                    statusText.text = "NFC Tag Read:\n$nfcTagData"
                    Toast.makeText(this, "NFC data saved to variable", Toast.LENGTH_SHORT).show()
                    sendToFirebase(nfcTagData)
                    return
                }
            }

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                readNfcTag(it)
            }

        }

    }

    // Helper function to convert byte array to hex string
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

//    @RequiresApi(Build.VERSION_CODES.O)
//    private fun readNfcTag(tag: Tag) {
//        val ndef = Ndef.get(tag)
//
//        if (ndef == null) {
//            val tagId = bytesToHex(tag.id)
//            nfcTagData = "Tag ID: $tagId"
//            runOnUiThread {
//                statusText.text = "NFC Tag Read:\n$nfcTagData"
//                Toast.makeText(this, "Tag ID saved to variable", Toast.LENGTH_SHORT).show()
//            }
//            sendToFirebase(nfcTagData)
//            return
//        }
//
//        try {
//            ndef.connect()
//            val ndefMessage = ndef.ndefMessage
//
//            if (ndefMessage != null) {
//                nfcTagData = parseNdefMessage(ndefMessage)
//                runOnUiThread {
//                    statusText.text = "NFC Tag Read:\n$nfcTagData"
//                    Toast.makeText(this, "NFC data saved to variable", Toast.LENGTH_SHORT).show()
//                }
//                sendToFirebase(nfcTagData)
//            } else {
//                nfcTagData = "Empty tag"
//                runOnUiThread {
//                    statusText.text = nfcTagData
//                }
//            }
//
//            ndef.close()
//
//        } catch (e: Exception) {
//            runOnUiThread {
//                Toast.makeText(this, "Error reading NFC tag: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//            e.printStackTrace()
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun readNfcTag(tag: Tag) {
        val ndef = Ndef.get(tag)

        if (ndef == null) {
            runOnUiThread {
                Toast.makeText(this, "Tag does not contain NDEF data", Toast.LENGTH_SHORT).show()
                statusText.text = "Error: No NDEF data found on tag"
            }
            return // Don't fall back to tag ID
        }

        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage

            if (ndefMessage != null) {
                nfcTagData = parseNdefMessage(ndefMessage)

                if (nfcTagData.isEmpty()) {
                    runOnUiThread {
                        statusText.text = "Error: Empty NDEF message"
                        Toast.makeText(this, "NDEF message is empty", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        statusText.text = "NFC Tag Read:\n$nfcTagData"
                        Toast.makeText(this, "NDEF data saved: $nfcTagData", Toast.LENGTH_SHORT).show()
                    }
                    sendToFirebase(nfcTagData)
                }
            } else {
                runOnUiThread {
                    statusText.text = "Error: Tag contains no NDEF message"
                    Toast.makeText(this, "Tag is empty or not formatted", Toast.LENGTH_SHORT).show()
                }
            }

            ndef.close()

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error reading NDEF: ${e.message}", Toast.LENGTH_LONG).show()
                statusText.text = "Error reading NFC tag"
            }
            e.printStackTrace()
        }
    }

//    private fun parseNdefMessage(message: NdefMessage): String {
//        val sb = StringBuilder()
//
//        for (record in message.records) {
//            if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
//                record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {
//
//                val payload = record.payload
//
//                val languageCodeLength = payload[0].toInt() and 0x3F
//
//                val text = String(
//                    payload,
//                    languageCodeLength + 1,
//                    payload.size - languageCodeLength - 1,
//                    charset("UTF-8")
//                )
//
//                sb.append(text).append("\n")
//            }
//            else if (record.tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA) {
//                try {
//                    val text = String(record.payload, StandardCharsets.UTF_8)
//                    sb.append(text).append("\n")
//                } catch (e: Exception) {
//                    val payload = String(record.payload, charset("UTF-8"))
//                    sb.append(payload).append("\n")
//                }
//
//            }
//            else {
//                val payload = String(record.payload, charset("UTF-8"))
//                sb.append(payload).append("\n")
//            }
//        }
//
//        return sb.toString().trim()
//    }

    private fun parseNdefMessage(message: NdefMessage): String {
        if (message.records.isEmpty()) {
            Log.d("AttendanceSender", "No records in NDEF message")
            return ""
        }

        // Get the first record (your student ID)
        val record = message.records[0]

        // Verify it's a text record
        if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
            record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {

            return parseTextRecord(record)
        } else {
            Log.d("AttendanceSender", "Unexpected record type")
            // Fallback: try to parse anyway
            return parseTextRecord(record)
        }
    }

    private fun parseTextRecord(record: android.nfc.NdefRecord): String {
        val payload = record.payload

        if (payload.isEmpty()) {
            Log.d("AttendanceSender", "Empty payload")
            return ""
        }

        try {
            // First byte contains the status byte (encoding + language code length)
            val statusByte = payload[0].toInt()
            val isUTF16 = (statusByte and 0x80) != 0
            val languageCodeLength = statusByte and 0x3F

            Log.d("AttendanceSender", "Language code length: $languageCodeLength")

            val charset = if (isUTF16) Charsets.UTF_16 else Charsets.UTF_8

            // Skip the status byte (1) and language code (typically 2 bytes for "en")
            val textStart = 1 + languageCodeLength

            if (textStart >= payload.size) {
                Log.d("AttendanceSender", "Invalid payload structure")
                return ""
            }

            // Extract the actual student ID
            val studentId = String(payload, textStart, payload.size - textStart, charset).trim()

            Log.d("AttendanceSender", "Extracted student ID: $studentId")

            return studentId

        } catch (e: Exception) {
            Log.e("AttendanceSender", "Error parsing text record", e)
            return ""
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentEvent(time: String, onResult: (String) -> Unit) {
        val roomRef = db.collection("Courses")

        roomRef.get()
            .addOnSuccessListener { documents ->
                var courseId = ""

                for (document in documents) {
                    val arrayField = document.get("schedule") as? List<*>

                    arrayField?.forEach { scheduleItem ->
                        val scheduleMap = scheduleItem as? Map<*, *>
                        val room = scheduleMap?.get("room")

                        if (room == selectedRoom) {
                            val startTime = scheduleMap["startTime"]
                            val endTime = scheduleMap["endTime"]

                            if (isCurrentTimeBetween(
                                    timeToLocalTime(time),
                                    timeToLocalTime(startTime.toString()),
                                    timeToLocalTime(endTime.toString())
                                )) {
                                courseId = document.get("courseId").toString()
                                return@forEach // Exit loop when found
                            }
                        }
                    }
                }

                onResult(courseId)
            }
            .addOnFailureListener { exception ->
                println("Error getting documents: $exception")
                onResult("") // Return empty string on error
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun timeToLocalTime(time: String): LocalTime {
        val timeParts = time.split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val secondTemp = timeParts[2]
        val secondAndMeridiemParts = secondTemp.split(" ")
        secondAndMeridiemParts[0].toInt()
        val meridiem = secondAndMeridiemParts[1]

        if (meridiem == "PM") {
            if (hour != 12) {
                return LocalTime.of(hour + 12, minute)
            }
        }

        return LocalTime.of(hour, minute)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun isCurrentTimeBetween(currentTime: LocalTime, startTime: LocalTime, endTime: LocalTime): Boolean {
        return if (startTime.isBefore(endTime)) {
            (currentTime.isAfter(startTime) || currentTime == startTime) &&
                    (currentTime.isBefore(endTime) || currentTime == endTime)
        } else { // startTime is after endTime, meaning the range crosses midnight
            currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
        }
    }

    fun getFullName(currentUserId: String, onResult: (String) -> Unit) {
        db.collection("Users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if(document.exists()) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    val fullName = "$firstName $lastName"
                    onResult(fullName)
                } else {
                    println("Document does not exist")
                    onResult("")
                }
            }
            .addOnFailureListener { exception ->
                println("Error retrieving user name: $exception")
                onResult("")
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendToFirebase(data: String) {
        val currentStudent = data.trim()

        if (currentStudent.isEmpty()) {
            runOnUiThread {
                Toast.makeText(this, "Invalid student ID", Toast.LENGTH_SHORT).show()
            }
            return
        }


        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))

        Log.d("AttendanceSender", "Starting sendToFirebase")
        Log.d("AttendanceSender", "currentStudent: $currentStudent")
        Log.d("AttendanceSender", "currentTime: $currentTime")

        getCurrentEvent(timeSet.getText().toString()) { courseId ->
            Log.d("AttendanceSender", "courseId returned: '$courseId'")

            if(courseId.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this, "No active class in this room", Toast.LENGTH_SHORT).show()
                }
                Log.d("AttendanceSender", "No active class found")
                return@getCurrentEvent
            }

            getFullName(currentStudent) { fullName ->
                Log.d("AttendanceSender", "fullName retrieved: '$fullName'")

                val nfcData = hashMapOf(
                    "courseId" to courseId,
                    "date" to formattedDate,
                    "lastModified" to System.currentTimeMillis(),
                    "method" to "ID CARD",
                    "notes" to "",
                    "recordId" to "${formattedDate}_${currentStudent}",
                    "status" to "PRESENT",
                    "studentId" to currentStudent,
                    "studentName" to fullName,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("Courses")
                    .whereEqualTo("courseId", courseId)
                    .get()
                    .addOnSuccessListener { documents ->
                        Log.d("AttendanceSender", "Query returned ${documents.size()} documents")

                        var isEnrolled = false
                        var courseDocId = ""

                        for (document in documents) {
                            val enrolledStudents = document.get("enrolledStudents") as? List<*>
                            Log.d("AttendanceSender", "enrolledStudents: $enrolledStudents")

                            if (enrolledStudents?.contains(currentStudent) == true) {
                                isEnrolled = true
                                courseDocId = document.id
                                break
                            }
                        }

                        if (isEnrolled) {
                            db.collection("Courses")
                                .document(courseDocId)
                                .collection("AttendanceRecords")
                                .document("${formattedDate}_${currentStudent}")
                                .set(nfcData)
                                .addOnSuccessListener {
                                    Log.d("AttendanceSender", "Attendance saved successfully!")
                                    runOnUiThread {
                                        Toast.makeText(
                                            this,
                                            "Attendance recorded successfully",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("AttendanceSender", "Error saving attendance", e)
                                    runOnUiThread {
                                        Toast.makeText(
                                            this,
                                            "Error saving attendance: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        } else {
                            Log.d("AttendanceSender", "Student not enrolled")
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    "Student not enrolled in this course",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("AttendanceSender", "Error querying courses", exception)
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "Error checking enrollment: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun switchToReaderMode() {
        currentMode = NfcMode.READER
        statusText.text = "Reader Mode Active\nReady to scan student cards"
        Toast.makeText(this, "Switched to Reader Mode", Toast.LENGTH_SHORT).show()

        // Restart the activity to apply new mode
        setupNfcForCurrentMode()

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun switchToHceMode() {
        currentMode = NfcMode.HCE
        HCEService.dataToSend = selectedRoom
        statusText.text = "HCE Mode Active\nReady to broadcast room: $selectedRoom"
        Toast.makeText(this, "Switched to HCE Mode - Hold device to scanner", Toast.LENGTH_SHORT).show()

        // Restart the activity to apply new mode
        setupNfcForCurrentMode()

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNfcForCurrentMode() {
        when (currentMode) {
            NfcMode.READER -> {
                // Disable any foreground dispatch first
                nfcAdapter?.disableForegroundDispatch(this)

                // Setup Reader Mode
                val callback = NfcAdapter.ReaderCallback { tag ->
                    onTagDiscovered(tag)
                }

                nfcAdapter?.enableReaderMode(
                    this,
                    callback,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                    null
                )
            }

            NfcMode.HCE -> {
                // Disable reader mode first
                nfcAdapter?.disableReaderMode(this)

                // HCE is passive - just ensure the service is set as default
                val component = ComponentName(this, HCEService::class.java)
                if (!cardEmulation!!.isDefaultServiceForCategory(
                        component,
                        CardEmulation.CATEGORY_OTHER
                    )) {
                    Toast.makeText(
                        this,
                        "Please set this app as default for NFC in settings",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            NfcMode.NONE -> {
                nfcAdapter?.disableReaderMode(this)
                nfcAdapter?.disableForegroundDispatch(this)
            }
        }
    }

}