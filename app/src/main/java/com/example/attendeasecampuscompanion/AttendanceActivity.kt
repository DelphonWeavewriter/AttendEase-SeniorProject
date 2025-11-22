package com.example.attendeasecampuscompanion

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter



class AttendanceActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
//    private var pendingIntent: PendingIntent? = null
//    private var intentFiltersArray: Array<IntentFilter>? = null
//
//    private var nfcTagData: String = ""

    private var receivedData: String = ""
    private lateinit var statusText: TextView
    private lateinit var statusDescription: TextView
    private lateinit var btnBack: TextView
    private lateinit var timeSet: EditText
    private lateinit var db: FirebaseFirestore
    private var currentRoom: String = "235"
    private var time: String = "11:45:00 AM"

    @RequiresApi(Build.VERSION_CODES.O)
    val currentDate: LocalDate = LocalDate.now()
    @RequiresApi(Build.VERSION_CODES.O)
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    @RequiresApi(Build.VERSION_CODES.O)
    val formattedDate: String = currentDate.format(formatter)

    companion object {
        private const val AID = "F0010203040506"

        private val SELECT_APDU_HEADER = byteArrayOf(
            0x00.toByte(),
            0xA4.toByte(),
            0x04.toByte(),
            0x00.toByte()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        statusText = findViewById(R.id.statusText)
        statusDescription = findViewById(R.id.statusDescription)

        timeSet = findViewById(R.id.timeSet)


        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }


        // Firestore Initialized
        db = FirebaseFirestore.getInstance()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "This device is not NFC-capable", Toast.LENGTH_LONG).show()
            statusText.text = "Unable to scan"
            statusDescription.text = "This device is not NFC-capapble"
            finish()
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            statusText.text = "Unable to scan"
            statusDescription.text = "NFC is disabled. Please enable it in the settings."
            Toast.makeText(this, "Please enable NFC", Toast.LENGTH_SHORT).show()
        } else {
            statusText.text = "Ready to scan"
            statusDescription.text = "Bring your device close to the in-class scanner"
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()

        val callback = NfcAdapter.ReaderCallback { tag ->
            onTagDiscovered(tag)
        }

        // Enable foreground dispatch to intercept NFC intents
        nfcAdapter?.enableReaderMode(
            this,
            callback,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )

        //runOnUiThread { textView.text = "Scanning for HCE device..." }
    }

    override fun onPause() {
        super.onPause()
        // Disable NFC detection when app is paused
        nfcAdapter?.disableReaderMode(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            runOnUiThread {
                Toast.makeText(this, "Not an ISO-DEP tag", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            isoDep.connect()
            isoDep.timeout = 5000

            // Convert AID from hex string to byte array
            val aidBytes = hexStringToByteArray(AID)

            // Build SELECT APDU command
            val selectApdu = SELECT_APDU_HEADER + byteArrayOf(aidBytes.size.toByte()) + aidBytes + byteArrayOf(0x00.toByte())

            // Send SELECT command
            val response = isoDep.transceive(selectApdu)

            if (response != null && response.size >= 2) {
                // Check status code (last 2 bytes should be 90 00 for success)
                val statusCode = response.sliceArray(response.size - 2 until response.size)

                if (statusCode[0] == 0x90.toByte() && statusCode[1] == 0x00.toByte()) {
                    // Extract data (everything except last 2 status bytes)
                    val dataBytes = response.sliceArray(0 until response.size - 2)
                    receivedData = String(dataBytes, StandardCharsets.UTF_8)

                    runOnUiThread {
                        statusText.text = "Received Data"
                        statusDescription.text = "Received Data:\n$receivedData"
                        Toast.makeText(this, "Data received successfully!", Toast.LENGTH_SHORT).show()
                    }

                    sendToFirebase(receivedData)
                } else {
                    runOnUiThread {
                        statusText.text = "Failed to read data"
                        statusDescription.text = "Communication error"
                        Toast.makeText(this, "Communication error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            isoDep.close()

        } catch (e: Exception) {
            runOnUiThread {
                statusText.text = "Error"
                statusDescription.text = "Error: ${e.message}"

                Toast.makeText(this, "Error reading HCE: ${e.message}", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun parseNdefMessage(message: NdefMessage): String {
        val sb = StringBuilder()

        for (record in message.records) {
            if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {

                val payload = record.payload

                val languageCodeLength = payload[0].toInt() and 0x3F

                val text = String(
                    payload,
                    languageCodeLength + 1,
                    payload.size - languageCodeLength - 1,
                    charset("UTF-8")
                )

                sb.append(text).append("\n")
            }
            else if (record.tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA) {
                try {
                    val text = String(record.payload, StandardCharsets.UTF_8)
                    sb.append(text).append("\n")
                } catch (e: Exception) {
                    val payload = String(record.payload, charset("UTF-8"))
                    sb.append(payload).append("\n")
                }

            }
            else {
                val payload = String(record.payload, charset("UTF-8"))
                sb.append(payload).append("\n")
            }
        }

        return sb.toString().trim()
    }

    // Function to send NFC data to the database
    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendToFirebase(data: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Would be used for getting the current time in practical use, but will not be used for the sake of demonstration purposes
        // val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))

        // The setTime value will be used instead
        val currentTime = timeSet.getText().toString()


        getCurrentEvent(currentTime) { courseId ->
            if (courseId.isEmpty()) {
                Toast.makeText(this, "No active class in this room", Toast.LENGTH_SHORT).show()
                return@getCurrentEvent
            }

            getFullName(currentUser.uid) { fullName ->
                val nfcData = hashMapOf(
                    "courseId" to courseId,
                    "date" to formattedDate,
                    "lastModified" to System.currentTimeMillis(),
                    "method" to "SCAN",
                    "notes" to "",
                    "recordId" to "${formattedDate}_${currentUser.uid}",
                    "status" to "PRESENT",
                    "studentId" to currentUser.uid,
                    "studentName" to fullName,
                    "timestamp" to System.currentTimeMillis()
                )

                // Then check if user is enrolled
                db.collection("Courses")
                    .whereEqualTo("courseId", courseId)
                    .get()
                    .addOnSuccessListener { documents ->
                        var isEnrolled = false
                        var courseDocId = ""

                        for (document in documents) {
                            val enrolledStudents = document.get("enrolledStudents") as? List<*>
                            if (enrolledStudents?.contains(currentUser.uid) == true) {
                                isEnrolled = true
                                courseDocId = document.id
                                break
                            }
                        }

                        if (isEnrolled) {
                            // Save attendance record
                            db.collection("Courses")
                                .document(courseDocId)
                                .collection("AttendanceRecords")
                                .document("${formattedDate}_${currentUser.uid}")
                                .set(nfcData)
                                .addOnSuccessListener { documentReference ->
                                    Toast.makeText(
                                        this,
                                        "Attendance recorded successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        this,
                                        "Error saving attendance: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        } else {
                            Toast.makeText(
                                this,
                                "You are not enrolled in this course",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(
                            this,
                            "Error checking enrollment: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
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

                        if (room == currentRoom) {
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun isAttendee(currentUserId: String, time: String, onResult: (Boolean) -> Unit) {
        // Gets the current event
        getCurrentEvent(time) { currentEvent ->
            if (currentEvent.isEmpty()) {
                onResult(false)
                return@getCurrentEvent
            }

            // Check if user is enrolled in the course
            db.collection("Courses")
                .whereEqualTo("courseId", currentEvent)
                .get()
                .addOnSuccessListener { documents ->
                    var isEnrolled = false

                    for (document in documents) {
                        val enrolledStudents = document.get("enrolledStudents") as? List<*>

                        if (enrolledStudents?.contains(currentUserId) == true) {
                            isEnrolled = true
                            Toast.makeText(this, "You are enrolled in: $currentEvent", Toast.LENGTH_SHORT).show()
                            break
                        }
                    }

                    onResult(isEnrolled)
                }
                .addOnFailureListener { exception ->
                    println("Error checking enrollment: $exception")
                    onResult(false)
                }
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



}