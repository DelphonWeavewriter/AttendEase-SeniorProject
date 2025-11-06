package com.example.attendeasecampuscompanion

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ViewAttendanceActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var btnBack: ImageButton
    private lateinit var spinnerCourses: Spinner
    private lateinit var btnSelectDate: Button
    private lateinit var tvSelectedDate: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutStats: LinearLayout
    private lateinit var layoutCharts: LinearLayout
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvPresentCount: TextView
    private lateinit var tvLateCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var recyclerViewStudents: RecyclerView
    private lateinit var tvEmptyState: TextView

    private var selectedCourse: Course? = null
    private var selectedDate: String = ""
    private var coursesList = mutableListOf<Course>()
    private var currentCourseSummary: CourseAttendanceSummary? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_attendance)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupListeners()
        loadProfessorCourses()
        setTodayAsDefault()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        spinnerCourses = findViewById(R.id.spinnerCourses)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        progressBar = findViewById(R.id.progressBar)
        layoutStats = findViewById(R.id.layoutStats)
        layoutCharts = findViewById(R.id.layoutCharts)
        tvTotalStudents = findViewById(R.id.tvTotalStudents)
        tvPresentCount = findViewById(R.id.tvPresentCount)
        tvLateCount = findViewById(R.id.tvLateCount)
        tvAbsentCount = findViewById(R.id.tvAbsentCount)
        pieChart = findViewById(R.id.pieChart)
        barChart = findViewById(R.id.barChart)
        recyclerViewStudents = findViewById(R.id.recyclerViewStudents)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        recyclerViewStudents.layoutManager = LinearLayoutManager(this)

        layoutStats.visibility = View.GONE
        layoutCharts.visibility = View.GONE
        tvEmptyState.visibility = View.GONE
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        spinnerCourses.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && coursesList.isNotEmpty()) {
                    selectedCourse = coursesList[position - 1]
                    if (selectedDate.isNotEmpty()) {
                        loadAttendanceData()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setTodayAsDefault() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDate = dateFormat.format(Date())

        val displayFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        tvSelectedDate.text = displayFormat.format(Date())
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            selectedDate = dateFormat.format(calendar.time)

            val displayFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
            tvSelectedDate.text = displayFormat.format(calendar.time)

            if (selectedCourse != null) {
                loadAttendanceData()
            }
        }, year, month, day).show()
    }

    private fun loadProfessorCourses() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        db.collection("Courses")
            .whereEqualTo("professorId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                coursesList.clear()
                for (doc in documents) {
                    val course = doc.toObject(Course::class.java)
                    coursesList.add(course)
                }

                val courseNames = mutableListOf("Select a course...")
                courseNames.addAll(coursesList.map { "${it.courseId} - ${it.courseName}" })

                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courseNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCourses.adapter = adapter

                progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load courses", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAttendanceData() {
        val course = selectedCourse ?: return
        if (selectedDate.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        layoutStats.visibility = View.GONE
        layoutCharts.visibility = View.GONE
        tvEmptyState.visibility = View.GONE

        db.collection("Courses")
            .whereEqualTo("courseId", course.courseId)
            .get()
            .addOnSuccessListener { courseDocs ->
                if (courseDocs.isEmpty) {
                    showEmptyState()
                    return@addOnSuccessListener
                }

                val courseDoc = courseDocs.documents[0]
                val courseDocId = courseDoc.id

                courseDoc.reference.collection("AttendanceRecords")
                    .whereEqualTo("date", selectedDate)
                    .get()
                    .addOnSuccessListener { attendanceDocs ->
                        if (attendanceDocs.isEmpty) {
                            showEmptyState()
                        } else {
                            processAttendanceData(attendanceDocs.documents.map {
                                it.toObject(AttendanceRecord::class.java)!!
                            })
                        }
                        progressBar.visibility = View.GONE
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        showEmptyState()
                        Toast.makeText(this, "Failed to load attendance records", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun processAttendanceData(records: List<AttendanceRecord>) {
        val presentCount = records.count { it.status == AttendanceStatus.PRESENT.name }
        val lateCount = records.count { it.status == AttendanceStatus.LATE.name }
        val absentCount = records.count { it.status == AttendanceStatus.ABSENT.name }
        val totalStudents = records.size

        currentCourseSummary = CourseAttendanceSummary(
            courseId = selectedCourse?.courseId ?: "",
            courseName = selectedCourse?.courseName ?: "",
            totalStudents = totalStudents,
            presentCount = presentCount,
            lateCount = lateCount,
            absentCount = absentCount,
            attendanceDate = selectedDate
        )

        displayStatistics(presentCount, lateCount, absentCount, totalStudents)
        setupPieChart(presentCount, lateCount, absentCount)
        setupBarChart(presentCount, lateCount, absentCount)
        loadStudentSummaries(records)

        layoutStats.visibility = View.VISIBLE
        layoutCharts.visibility = View.VISIBLE
    }

    private fun displayStatistics(present: Int, late: Int, absent: Int, total: Int) {
        tvTotalStudents.text = total.toString()
        tvPresentCount.text = "$present (${if(total > 0) String.format("%.1f", present.toFloat()/total*100) else "0"}%)"
        tvLateCount.text = "$late (${if(total > 0) String.format("%.1f", late.toFloat()/total*100) else "0"}%)"
        tvAbsentCount.text = "$absent (${if(total > 0) String.format("%.1f", absent.toFloat()/total*100) else "0"}%)"
    }

    private fun setupPieChart(present: Int, late: Int, absent: Int) {
        val entries = ArrayList<PieEntry>()

        if (present > 0) entries.add(PieEntry(present.toFloat(), "Present"))
        if (late > 0) entries.add(PieEntry(late.toFloat(), "Late"))
        if (absent > 0) entries.add(PieEntry(absent.toFloat(), "Absent"))

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            Color.parseColor("#66BB6A"),
            Color.parseColor("#FFA726"),
            Color.parseColor("#EF5350")
        )
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = Color.WHITE

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))

        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = true
        pieChart.setUsePercentValues(true)
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.setEntryLabelTextSize(12f)
        pieChart.centerText = "Attendance\nBreakdown"
        pieChart.setCenterTextSize(14f)
        pieChart.animateY(1000, Easing.EaseInOutQuad)
        pieChart.invalidate()
    }

    private fun setupBarChart(present: Int, late: Int, absent: Int) {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, present.toFloat()))
        entries.add(BarEntry(1f, late.toFloat()))
        entries.add(BarEntry(2f, absent.toFloat()))

        val dataSet = BarDataSet(entries, "Students")
        dataSet.colors = listOf(
            Color.parseColor("#66BB6A"),
            Color.parseColor("#FFA726"),
            Color.parseColor("#EF5350")
        )
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = Color.BLACK

        val data = BarData(dataSet)
        data.barWidth = 0.5f

        barChart.data = data
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setFitBars(true)

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.valueFormatter = IndexAxisValueFormatter(arrayOf("Present", "Late", "Absent"))
        xAxis.textSize = 12f

        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisRight.isEnabled = false
        barChart.axisLeft.axisMinimum = 0f
        barChart.animateY(1000, Easing.EaseInOutQuad)
        barChart.invalidate()
    }

    private fun loadStudentSummaries(records: List<AttendanceRecord>) {
        val studentMap = records.groupBy { it.studentId }
        val summaries = studentMap.map { (studentId, studentRecords) ->
            val record = studentRecords.first()
            val presentCount = if (record.status == AttendanceStatus.PRESENT.name) 1 else 0
            val lateCount = if (record.status == AttendanceStatus.LATE.name) 1 else 0
            val absentCount = if (record.status == AttendanceStatus.ABSENT.name) 1 else 0

            StudentAttendanceSummary(
                studentId = studentId,
                studentName = record.studentName,
                totalClasses = 1,
                presentCount = presentCount,
                lateCount = lateCount,
                absentCount = absentCount,
                attendanceRate = if (record.status == AttendanceStatus.PRESENT.name) 100f else 0f,
                records = studentRecords
            )
        }.sortedBy { it.studentName }

        val adapter = AttendanceReportAdapter(summaries) { student ->
            showStudentDetail(student)
        }
        recyclerViewStudents.adapter = adapter
    }

    private fun showStudentDetail(student: StudentAttendanceSummary) {
        val course = selectedCourse ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_student_detail, null)
        val tvStudentName = dialogView.findViewById<TextView>(R.id.tvStudentName)
        val tvAttendanceRate = dialogView.findViewById<TextView>(R.id.tvAttendanceRate)
        val tvTotalClasses = dialogView.findViewById<TextView>(R.id.tvTotalClasses)
        val tvPresentCount = dialogView.findViewById<TextView>(R.id.tvDetailPresentCount)
        val tvLateCount = dialogView.findViewById<TextView>(R.id.tvDetailLateCount)
        val tvAbsentCount = dialogView.findViewById<TextView>(R.id.tvDetailAbsentCount)
        val progressBarDetail = dialogView.findViewById<ProgressBar>(R.id.progressBarDetail)
        val layoutDetailContent = dialogView.findViewById<LinearLayout>(R.id.layoutDetailContent)

        tvStudentName.text = student.studentName
        layoutDetailContent.visibility = View.GONE
        progressBarDetail.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()

        dialog.show()

        db.collection("Courses")
            .whereEqualTo("courseId", course.courseId)
            .get()
            .addOnSuccessListener { courseDocs ->
                if (courseDocs.isEmpty) {
                    progressBarDetail.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val courseDoc = courseDocs.documents[0]

                courseDoc.reference.collection("AttendanceRecords")
                    .whereEqualTo("studentId", student.studentId)
                    .get()
                    .addOnSuccessListener { records ->
                        val allRecords = records.documents.mapNotNull {
                            it.toObject(AttendanceRecord::class.java)
                        }

                        val totalClasses = allRecords.size
                        val presentCount = allRecords.count { it.status == AttendanceStatus.PRESENT.name }
                        val lateCount = allRecords.count { it.status == AttendanceStatus.LATE.name }
                        val absentCount = allRecords.count { it.status == AttendanceStatus.ABSENT.name }
                        val attendanceRate = if (totalClasses > 0) (presentCount.toFloat() / totalClasses * 100) else 0f

                        tvAttendanceRate.text = String.format("%.1f%%", attendanceRate)
                        tvAttendanceRate.setTextColor(
                            when {
                                attendanceRate >= 90f -> Color.parseColor("#66BB6A")
                                attendanceRate >= 75f -> Color.parseColor("#FFA726")
                                else -> Color.parseColor("#EF5350")
                            }
                        )

                        tvTotalClasses.text = totalClasses.toString()
                        tvPresentCount.text = "$presentCount 🟢"
                        tvLateCount.text = "$lateCount 🟡"
                        tvAbsentCount.text = "$absentCount 🔴"

                        progressBarDetail.visibility = View.GONE
                        layoutDetailContent.visibility = View.VISIBLE
                    }
                    .addOnFailureListener {
                        progressBarDetail.visibility = View.GONE
                        Toast.makeText(this, "Failed to load student details", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun showEmptyState() {
        layoutStats.visibility = View.GONE
        layoutCharts.visibility = View.GONE
        tvEmptyState.visibility = View.VISIBLE
        tvEmptyState.text = "No attendance records found for selected date.\n\nTry selecting a different date or mark attendance first."
    }
}