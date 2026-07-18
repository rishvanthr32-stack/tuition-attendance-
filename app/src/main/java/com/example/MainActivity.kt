package com.example

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Attendance
import com.example.data.Student
import com.example.ui.AttendanceViewModel
import com.example.ui.SyncState
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TuitionAttendanceApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuitionAttendanceApp() {
    val context = LocalContext.current
    val viewModel: AttendanceViewModel = viewModel()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val jsonStr = viewModel.exportBackupJson()
                    if (jsonStr.isNotEmpty()) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(jsonStr.toByteArray())
                        }
                        Toast.makeText(context, "Backup successfully saved to Google Drive/Storage!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to generate backup data.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error writing backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val jsonStr = inputStream.bufferedReader().use { it.readText() }
                        viewModel.importBackupJson(
                            jsonString = jsonStr,
                            onSuccess = {
                                Toast.makeText(context, "Data successfully restored from Google Drive/Storage!", Toast.LENGTH_LONG).show()
                            },
                            onError = { errorMessage ->
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val students by viewModel.students.collectAsStateWithLifecycle()
    val attendanceMap by viewModel.attendanceMap.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendance.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Attendance", "Students", "Reports")

    // Search and Filters
    var attendanceSearchQuery by remember { mutableStateOf("") }
    var studentSearchQuery by remember { mutableStateOf("") }

    // Dialog configurations
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }
    var studentForWhatsAppReport by remember { mutableStateOf<Student?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tuition Attendance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Network & Sync Badge
                    SyncStatusBadge(isOnline = isOnline, syncState = syncState) {
                        viewModel.triggerSync()
                    }
                }

                // Tabs Navigation
                TabRow(
                    selectedTabIndex = currentTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[currentTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                val icon = when (index) {
                                    0 -> if (currentTab == index) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
                                    1 -> if (currentTab == index) Icons.Filled.People else Icons.Outlined.People
                                    else -> if (currentTab == index) Icons.Filled.Assessment else Icons.Outlined.Assessment
                                }
                                Icon(icon, contentDescription = title)
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showAddStudentDialog = true },
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = "Add Student") },
                    text = { Text("Add Student") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> AttendanceTab(
                    selectedDate = selectedDate,
                    students = students,
                    attendanceMap = attendanceMap,
                    searchQuery = attendanceSearchQuery,
                    onSearchQueryChange = { attendanceSearchQuery = it },
                    onDateChange = { viewModel.setDate(it) },
                    onAttendanceChange = { studentId, present ->
                        viewModel.saveAttendance(studentId, present)
                    },
                    onMarkAllPresent = { viewModel.markAllPresent() },
                    onMarkAllAbsent = { viewModel.markAllAbsent() },
                    onMarkSelectedAttendance = { studentIds, present ->
                        viewModel.markSelectedAttendance(studentIds, present)
                    }
                )
                1 -> StudentsTab(
                    students = students,
                    searchQuery = studentSearchQuery,
                    onSearchQueryChange = { studentSearchQuery = it },
                    onEditStudent = { studentToEdit = it },
                    onDeleteStudent = { studentToDelete = it }
                )
                2 -> ReportsTab(
                    students = students,
                    attendanceList = allAttendance,
                    onOpenWhatsAppReport = { studentForWhatsAppReport = it },
                    onSharePdf = { monthLabel ->
                        shareMonthlyPdf(context, monthLabel, students, allAttendance)
                    },
                    onShareApk = {
                        shareApk(context)
                    },
                    onBackupToDrive = {
                        createDocumentLauncher.launch("tuition_attendance_backup.json")
                    },
                    onRestoreFromDrive = {
                        openDocumentLauncher.launch(arrayOf("application/json"))
                    }
                )
            }
        }
    }

    // Add Student Dialog
    if (showAddStudentDialog) {
        StudentFormDialog(
            title = "Add New Student",
            onDismiss = { showAddStudentDialog = false },
            onSave = { name, parentName, parentPhone ->
                viewModel.addStudent(name, parentName, parentPhone)
                showAddStudentDialog = false
            }
        )
    }

    // Edit Student Dialog
    studentToEdit?.let { student ->
        StudentFormDialog(
            title = "Edit Student Details",
            student = student,
            onDismiss = { studentToEdit = null },
            onSave = { name, parentName, parentPhone ->
                viewModel.updateStudent(
                    student.copy(
                        name = name,
                        parentName = parentName,
                        parentPhone = parentPhone
                    )
                )
                studentToEdit = null
            }
        )
    }

    // Delete Student Confirmation Dialog
    studentToDelete?.let { student ->
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            title = { Text("Delete Student?") },
            text = { Text("Are you sure you want to delete ${student.name}? All of their past attendance records will be removed from local storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStudent(student)
                        studentToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // WhatsApp Report Builder Dialog
    studentForWhatsAppReport?.let { student ->
        WhatsAppReportDialog(
            student = student,
            attendanceList = allAttendance,
            onDismiss = { studentForWhatsAppReport = null },
            onSend = { phone, msg ->
                shareViaWhatsApp(context, phone, msg)
                studentForWhatsAppReport = null
            }
        )
    }
}

// ==========================================
// COMPOSABLES: SYNC STATUS BADGE
// ==========================================
@Composable
fun SyncStatusBadge(isOnline: Boolean, syncState: SyncState, onSyncClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val containerColor: Color
    val contentColor: Color
    val text: String
    val icon: @Composable () -> Unit

    when (syncState) {
        SyncState.SYNCING -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            text = "Syncing..."
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            }
        }
        SyncState.SYNCED -> {
            containerColor = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFE8F5E9)
            contentColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
            text = "Synced"
            icon = {
                Icon(
                    Icons.Filled.CloudDone,
                    contentDescription = "Synced Offline",
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
        }
        SyncState.OFFLINE_SAVING -> {
            containerColor = if (isDark) Color(0xFFE65100).copy(alpha = 0.25f) else Color(0xFFFFF3E0)
            contentColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
            text = "Offline Mode"
            icon = {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = "Saved Offline",
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
        }
        else -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            text = "Idle"
            icon = {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = "Sync",
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
        }
    }

    Surface(
        onClick = onSyncClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.clickable { onSyncClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

data class CalendarDay(
    val year: Int,
    val month: Int,
    val day: Int,
    val isCurrentMonth: Boolean
)

// ==========================================
// COMPOSABLES: TAB 1 - DAILY ATTENDANCE
// ==========================================
@Composable
fun AttendanceTab(
    selectedDate: String,
    students: List<Student>,
    attendanceMap: Map<Long, Attendance>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onAttendanceChange: (Long, Boolean) -> Unit,
    onMarkAllPresent: () -> Unit,
    onMarkAllAbsent: () -> Unit,
    onMarkSelectedAttendance: (List<Long>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val filteredStudents = remember(students, searchQuery) {
        students.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    var calendarVisible by remember { mutableStateOf(false) }
    val selectedStudents = remember { mutableStateListOf<Long>() }

    LaunchedEffect(selectedDate) {
        selectedStudents.clear()
    }

    // Calendar month/year navigation state
    var currentYearMonth by remember(selectedDate) {
        val cal = Calendar.getInstance()
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
            if (date != null) cal.time = date
        } catch (e: Exception) {}
        mutableStateOf(Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)))
    }

    val calendarDays = remember(currentYearMonth) {
        val year = currentYearMonth.first
        val month = currentYearMonth.second

        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sunday = 1, Monday = 2
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val list = mutableListOf<CalendarDay>()

        // Previous month padding
        val prevCal = cal.clone() as Calendar
        prevCal.add(Calendar.MONTH, -1)
        val maxDaysPrev = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val paddingDaysCount = firstDayOfWeek - 1 // Sunday start
        for (i in (maxDaysPrev - paddingDaysCount + 1)..maxDaysPrev) {
            list.add(CalendarDay(year = prevCal.get(Calendar.YEAR), month = prevCal.get(Calendar.MONTH), day = i, isCurrentMonth = false))
        }

        // Current month
        for (i in 1..maxDays) {
            list.add(CalendarDay(year = year, month = month, day = i, isCurrentMonth = true))
        }

        // Next month padding
        val nextCal = cal.clone() as Calendar
        nextCal.add(Calendar.MONTH, 1)
        val remaining = 42 - list.size
        for (i in 1..remaining) {
            list.add(CalendarDay(year = nextCal.get(Calendar.YEAR), month = nextCal.get(Calendar.MONTH), day = i, isCurrentMonth = false))
        }

        list
    }

    // Format selectedDate for visual presentation
    val displayDate = remember(selectedDate) {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
            if (date != null) {
                SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(date)
            } else {
                selectedDate
            }
        } catch (e: Exception) {
            selectedDate
        }
    }

    val isToday = remember(selectedDate) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        selectedDate == todayStr
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Date Picker & Navigator Row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDateChange(changeDay(selectedDate, -1)) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Previous Day")
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { calendarVisible = !calendarVisible },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (calendarVisible) Icons.Filled.CalendarToday else Icons.Filled.CalendarMonth,
                            contentDescription = "Toggle Calendar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Icon(
                            if (calendarVisible) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Native date picker button shortcut
                    IconButton(onClick = { showDatePicker(context, selectedDate, onDateChange) }) {
                        Icon(Icons.Outlined.EditCalendar, contentDescription = "Pick Custom Date", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = { onDateChange(changeDay(selectedDate, 1)) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Next Day")
                    }
                }

                // Collapsible visual Calendar Grid
                AnimatedVisibility(
                    visible = calendarVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Month Year navigation header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val monthYearLabel = remember(currentYearMonth) {
                                val cal = Calendar.getInstance()
                                cal.set(Calendar.YEAR, currentYearMonth.first)
                                cal.set(Calendar.MONTH, currentYearMonth.second)
                                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                            }

                            IconButton(
                                onClick = {
                                    val year = currentYearMonth.first
                                    val month = currentYearMonth.second
                                    currentYearMonth = if (month == 0) {
                                        Pair(year - 1, 11)
                                    } else {
                                        Pair(year, month - 1)
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                            }

                            Text(
                                text = monthYearLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            IconButton(
                                onClick = {
                                    val year = currentYearMonth.first
                                    val month = currentYearMonth.second
                                    currentYearMonth = if (month == 11) {
                                        Pair(year + 1, 0)
                                    } else {
                                        Pair(year, month + 1)
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Weekdays header
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val weekdays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                            weekdays.forEach { day ->
                                Text(
                                    text = day,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Days grid
                        val rows = calendarDays.chunked(7)
                        rows.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                week.forEach { day ->
                                    val dayDateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", day.year, day.month + 1, day.day)
                                    val isSelected = dayDateStr == selectedDate
                                    val isTodayDay = dayDateStr == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                                    val cellBgColor = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isTodayDay -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    }

                                    val cellTextColor = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        !day.isCurrentMonth -> MaterialTheme.colorScheme.outlineVariant
                                        isTodayDay -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(cellBgColor)
                                            .clickable {
                                                onDateChange(dayDateStr)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = day.day.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected || isTodayDay) FontWeight.Bold else FontWeight.Normal,
                                                color = cellTextColor
                                            )
                                            if (isTodayDay && !isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Today Active/Past Date Warning Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
        ) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            Surface(
                onClick = { if (!isToday) onDateChange(todayStr) },
                color = if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isToday
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isToday) Icons.Filled.CheckCircle else Icons.Filled.Today,
                        contentDescription = null,
                        tint = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isToday) "Marking Attendance for Today's Session" else "Currently viewing past date. Tap here to mark Today's Attendance.",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Search Student field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("attendance_search_input"),
            placeholder = { Text("Search Student by Name...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Select All / Bulk Action Controls
        if (filteredStudents.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allSelected = filteredStudents.isNotEmpty() && filteredStudents.all { selectedStudents.contains(it.id) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (allSelected) {
                                filteredStudents.forEach { selectedStudents.remove(it.id) }
                            } else {
                                filteredStudents.forEach { if (!selectedStudents.contains(it.id)) selectedStudents.add(it.id) }
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            if (checked) {
                                filteredStudents.forEach { if (!selectedStudents.contains(it.id)) selectedStudents.add(it.id) }
                            } else {
                                filteredStudents.forEach { selectedStudents.remove(it.id) }
                            }
                        },
                        modifier = Modifier.testTag("select_all_checkbox")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Select All (${filteredStudents.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedStudents.isNotEmpty()) {
                    TextButton(onClick = { selectedStudents.clear() }) {
                        Text("Clear Selection", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bulk Actions Row (When nothing is selected)
        if (students.isNotEmpty() && selectedStudents.isEmpty()) {
            val isDark = isSystemInDarkTheme()
            val bulkPresentBg = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFE8F5E9)
            val bulkPresentFg = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
            val bulkAbsentBg = if (isDark) Color(0xFFB71C1C).copy(alpha = 0.3f) else Color(0xFFFFEBEE)
            val bulkAbsentFg = if (isDark) Color(0xFFE57373) else Color(0xFFC62828)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onMarkAllPresent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mark_all_present_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = bulkPresentBg, contentColor = bulkPresentFg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("All Present", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onMarkAllAbsent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mark_all_absent_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = bulkAbsentBg, contentColor = bulkAbsentFg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("All Absent", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Student Attendance List
        if (filteredStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Group,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (students.isEmpty()) "No Students Added Yet" else "No matching students found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (students.isEmpty()) "Go to the 'Students' tab to add tuition students." else "Try checking the spelling.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        val attendance = attendanceMap[student.id]
                        val isSelected = selectedStudents.contains(student.id)
                        AttendanceStudentCard(
                            student = student,
                            attendance = attendance,
                            isSelected = isSelected,
                            onSelectedChange = { selected ->
                                if (selected) {
                                    if (!selectedStudents.contains(student.id)) selectedStudents.add(student.id)
                                } else {
                                    selectedStudents.remove(student.id)
                                }
                            },
                            onAttendanceChange = { isPresent ->
                                onAttendanceChange(student.id, isPresent)
                            }
                        )
                    }
                }

                // Beautiful floating selection controls overlay matching Recharts Card aesthetic
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedStudents.isNotEmpty(),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .testTag("bulk_marking_bar")
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedStudents.clear() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear Selection",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${selectedStudents.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onMarkSelectedAttendance(selectedStudents.toList(), true)
                                        selectedStudents.clear()
                                    },
                                    modifier = Modifier.testTag("bulk_present_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Present")
                                }

                                Button(
                                    onClick = {
                                        onMarkSelectedAttendance(selectedStudents.toList(), false)
                                        selectedStudents.clear()
                                    },
                                    modifier = Modifier.testTag("bulk_absent_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Absent")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceStudentCard(
    student: Student,
    attendance: Attendance?,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onAttendanceChange: (Boolean) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // Theme-adaptive green and red colors for the toggles
    val presentBg = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFE8F5E9)
    val presentFg = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    val absentBg = if (isDark) Color(0xFFB71C1C).copy(alpha = 0.3f) else Color(0xFFFFEBEE)
    val absentFg = if (isDark) Color(0xFFE57373) else Color(0xFFC62828)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("student_card_${student.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectedChange(!isSelected) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Checkbox and Student Profile Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.5f)
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectedChange(it) },
                    modifier = Modifier
                        .testTag("student_checkbox_${student.id}")
                        .padding(end = 4.dp)
                )

                // Profile Avatar with colored bg
                val initials = student.name.take(1).uppercase()
                val avatarBg = remember(student.id, isDark) {
                    val colors = if (isDark) {
                        listOf(
                            Color(0xFF1E293B), Color(0xFF334155), Color(0xFF1F2937),
                            Color(0xFF111827), Color(0xFF1E1B4B), Color(0xFF0F172A)
                        )
                    } else {
                        listOf(
                            Color(0xFFE3F2FD), Color(0xFFF3E5F5), Color(0xFFE8F5E9),
                            Color(0xFFFFF3E0), Color(0xFFF9FBFD), Color(0xFFFFFDE7)
                        )
                    }
                    colors[(student.id % colors.size).toInt()]
                }
                val avatarText = remember(student.id, isDark) {
                    val colors = if (isDark) {
                        listOf(
                            Color(0xFF90CAF9), Color(0xFFE1BEE7), Color(0xFFA5D6A7),
                            Color(0xFFFFCC80), Color(0xFFC5CAE9), Color(0xFFFFF59D)
                        )
                    } else {
                        listOf(
                            Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF43A047),
                            Color(0xFFF4511E), Color(0xFF039BE5), Color(0xFF827717)
                        )
                    }
                    colors[(student.id % colors.size).toInt()]
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontWeight = FontWeight.Bold,
                        color = avatarText,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (student.parentPhone.isNotEmpty()) {
                        Text(
                            text = "Parent: ${student.parentPhone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Attendance Toggles Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.1f)
            ) {
                val isPresent = attendance?.isPresent == true
                val isAbsent = attendance != null && !attendance.isPresent

                // Present Button
                OutlinedIconToggleButton(
                    checked = isPresent,
                    onCheckedChange = { if (it) onAttendanceChange(true) },
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.outlinedIconToggleButtonColors(
                        checkedContainerColor = presentBg,
                        checkedContentColor = presentFg,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.outline
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isPresent) presentFg else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("student_present_btn_${student.id}")
                ) {
                    Text("Present", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                // Absent Button
                OutlinedIconToggleButton(
                    checked = isAbsent,
                    onCheckedChange = { if (it) onAttendanceChange(false) },
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.outlinedIconToggleButtonColors(
                        checkedContainerColor = absentBg,
                        checkedContentColor = absentFg,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.outline
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isAbsent) absentFg else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("student_absent_btn_${student.id}")
                ) {
                    Text("Absent", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLES: TAB 2 - STUDENT DIRECTORY
// ==========================================
@Composable
fun StudentsTab(
    students: List<Student>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEditStudent: (Student) -> Unit,
    onDeleteStudent: (Student) -> Unit
) {
    val filteredStudents = remember(students, searchQuery) {
        students.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Welcome educational banner card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner),
                    contentDescription = "Tuition Class Hero",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tuition Student Registry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Manage your active students below. Record their parents' phone numbers to easily export reports directly via WhatsApp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search students...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (students.isEmpty()) "No Students Added Yet" else "No matching students found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (students.isEmpty()) "Click the '+' Floating Action Button below to register your tuition students." else "Check search keywords.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(filteredStudents, key = { it.id }) { student ->
                    StudentDirectoryCard(
                        student = student,
                        onEditClick = { onEditStudent(student) },
                        onDeleteClick = { onDeleteStudent(student) }
                    )
                }
            }
        }
    }
}

@Composable
fun StudentDirectoryCard(
    student: Student,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.FamilyRestroom,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Parent: ${student.parentName.ifEmpty { "N/A" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Phone: ${student.parentPhone.ifEmpty { "N/A" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit Details",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete Student",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLES: TAB 3 - MONTHLY REPORTS & EXPORT
// ==========================================
@Composable
fun ReportsTab(
    students: List<Student>,
    attendanceList: List<Attendance>,
    onOpenWhatsAppReport: (Student) -> Unit,
    onSharePdf: (String) -> Unit,
    onShareApk: () -> Unit,
    onBackupToDrive: () -> Unit,
    onRestoreFromDrive: () -> Unit
) {
    val recentMonths = remember { getRecentMonths() }
    var selectedMonthIndex by remember { mutableStateOf(0) }
    val selectedMonthValue = recentMonths[selectedMonthIndex].first // format "yyyy-MM"
    val selectedMonthLabel = recentMonths[selectedMonthIndex].second // format "July 2026"
    var expandedStudentId by remember { mutableStateOf<Long?>(null) }

    val monthAttendance = remember(attendanceList, selectedMonthValue) {
        attendanceList.filter { it.dateString.startsWith(selectedMonthValue) }
    }

    val chartData = remember(monthAttendance, students) {
        if (students.isEmpty() || monthAttendance.isEmpty()) {
            emptyList<ChartPoint>()
        } else {
            val grouped = monthAttendance.groupBy { it.dateString }
            grouped.map { (dateStr, list) ->
                val presentCount = list.count { it.isPresent }
                val totalCount = list.size
                val percentage = if (totalCount > 0) (presentCount.toFloat() / totalCount) * 100f else 0f
                
                val dayLabel = try {
                    val dateObj = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                    if (dateObj != null) SimpleDateFormat("d", Locale.getDefault()).format(dateObj) else dateStr.substringAfterLast("-")
                } catch (e: Exception) {
                    dateStr.substringAfterLast("-")
                }
                
                ChartPoint(
                    dateString = dateStr,
                    displayLabel = dayLabel,
                    percentage = percentage,
                    presentCount = presentCount,
                    totalCount = totalCount
                )
            }.sortedBy { it.dateString }
        }
    }

    // Calculations
    val averageAttendance = remember(monthAttendance, students) {
        if (students.isEmpty() || monthAttendance.isEmpty()) 0f
        else {
            val presents = monthAttendance.count { it.isPresent }
            (presents.toFloat() / monthAttendance.size) * 100
        }
    }

    val totalClasses = remember(monthAttendance) {
        monthAttendance.map { it.dateString }.distinct().size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Month selector scrollable chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recentMonths.forEachIndexed { index, pair ->
                FilterChip(
                    selected = selectedMonthIndex == index,
                    onClick = { selectedMonthIndex = index },
                    label = { Text(pair.second) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        if (students.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add tuition students to see metrics & monthly reports here.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            // Stats summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "$selectedMonthLabel Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Avg Attendance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            Text(
                                text = String.format("%.1f%%", averageAttendance),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Column {
                            Text(
                                text = "Classes Held",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "$totalClasses days",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Column {
                            Text(
                                text = "Students",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${students.size}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Student month stats list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    ClassAttendanceTrendChart(chartData = chartData)
                }

                item {
                    Text(
                        text = "Individual Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(students, key = { it.id }) { student ->
                    val studentAttendance = monthAttendance.filter { it.studentId == student.id }
                    val presentCount = studentAttendance.count { it.isPresent }
                    val totalDays = studentAttendance.size
                    val percent = if (totalDays > 0) (presentCount * 100) / totalDays else 0
                    val isExpanded = expandedStudentId == student.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable {
                                expandedStudentId = if (isExpanded) null else student.id
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = student.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Toggle attendance map",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Present: $presentCount of $totalDays class days",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Dynamic Colored Progress Bar
                                    val barColor = when {
                                        percent >= 80 -> Color(0xFF2E7D32) // green
                                        percent >= 50 -> Color(0xFFF9A825) // yellow/orange
                                        else -> Color(0xFFC62828) // red
                                    }

                                    LinearProgressIndicator(
                                        progress = { if (totalDays > 0) presentCount.toFloat() / totalDays else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = barColor,
                                        trackColor = barColor.copy(alpha = 0.2f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$percent%",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    // WhatsApp share action icon
                                    val isDark = isSystemInDarkTheme()
                                    val shareBg = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFE8F5E9)
                                    val shareFg = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    IconButton(
                                        onClick = { onOpenWhatsAppReport(student) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = shareBg,
                                            contentColor = shareFg
                                        ),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = "Share Report",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    
                                    Text(
                                        text = "Day-by-Day Attendance Map",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Showing daily status for $selectedMonthLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    // Day of week headers
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayName ->
                                            Text(
                                                text = dayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    // Grid of days
                                    val daysList = remember(selectedMonthValue) {
                                        val list = mutableListOf<CalendarDay?>()
                                        val cal = Calendar.getInstance()
                                        try {
                                            val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                                            val date = sdf.parse(selectedMonthValue)
                                            if (date != null) {
                                                cal.time = date
                                                cal.set(Calendar.DAY_OF_MONTH, 1)
                                                val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                                                val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                                                val year = cal.get(Calendar.YEAR)
                                                val month = cal.get(Calendar.MONTH)

                                                val padding = firstDayOfWeek - 1
                                                for (i in 0 until padding) {
                                                    list.add(null)
                                                }

                                                for (d in 1..maxDays) {
                                                    list.add(CalendarDay(year, month, d, true))
                                                }
                                            }
                                        } catch (e: Exception) {}
                                        list
                                    }

                                    val rows = daysList.chunked(7)
                                    rows.forEach { week ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            week.forEach { day ->
                                                if (day == null) {
                                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                                } else {
                                                    val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", day.year, day.month + 1, day.day)
                                                    val attRecord = studentAttendance.find { it.dateString == dateStr }
                                                    
                                                    val isDark = isSystemInDarkTheme()
                                                    val (cellBg, cellText, label) = when {
                                                        attRecord == null -> Triple(
                                                            Color.Transparent, 
                                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                            "No Class / Not Marked"
                                                        )
                                                        attRecord.isPresent -> Triple(
                                                            if (isDark) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFE8F5E9),
                                                            if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                                                            "Present"
                                                        )
                                                        else -> Triple(
                                                            if (isDark) Color(0xFFB71C1C).copy(alpha = 0.3f) else Color(0xFFFFEBEE),
                                                            if (isDark) Color(0xFFE57373) else Color(0xFFC62828),
                                                            "Absent"
                                                        )
                                                    }

                                                    val borderStroke = if (attRecord == null) {
                                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                    } else {
                                                        BorderStroke(1.dp, cellText.copy(alpha = 0.5f))
                                                    }

                                                    val contextForToast = LocalContext.current
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .aspectRatio(1f)
                                                            .padding(2.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(cellBg)
                                                            .border(borderStroke, RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                val formattedDate = try {
                                                                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                                                                    if (date != null) SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date) else dateStr
                                                                } catch (e: Exception) { dateStr }
                                                                Toast.makeText(contextForToast, "$formattedDate: $label", Toast.LENGTH_SHORT).show()
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = day.day.toString(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = if (attRecord != null) FontWeight.Bold else FontWeight.Normal,
                                                            color = cellText
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    // Legend row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val isDark = isSystemInDarkTheme()
                                        val legendItems = listOf(
                                            Triple(if (isDark) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFE8F5E9), if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32), "Present"),
                                            Triple(if (isDark) Color(0xFFB71C1C).copy(alpha = 0.3f) else Color(0xFFFFEBEE), if (isDark) Color(0xFFE57373) else Color(0xFFC62828), "Absent"),
                                            Triple(Color.Transparent, MaterialTheme.colorScheme.outline, "No Class")
                                        )
                                        
                                        legendItems.forEach { (bg, fg, text) ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(bg)
                                                        .border(1.dp, if (bg == Color.Transparent) MaterialTheme.colorScheme.outlineVariant else fg.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = text,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Google Cloud Backup & Sync Card
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudQueue,
                                    contentDescription = "Google Cloud Backup",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Google Drive Cloud Backup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Keep your student records & attendance safe. Backup your data directly to Google Drive and restore it instantly on any device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onBackupToDrive,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = onRestoreFromDrive,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Auto-Backup enabled",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Android Auto-Backup to Google Cloud is active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                // Export and Share App Panel
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Export & App Utility",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Extract a professional, beautiful PDF table report or distribute the app files locally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onSharePdf(selectedMonthValue) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Description, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF Report", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = onShareApk,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Quick Share App", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLES: STUDENT DETAILS FORM DIALOG
// ==========================================
@Composable
fun StudentFormDialog(
    title: String,
    student: Student? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(student?.name ?: "") }
    var parentName by remember { mutableStateOf(student?.parentName ?: "") }
    var parentPhone by remember { mutableStateOf(student?.parentPhone ?: "") }

    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotEmpty()) isError = false
                    },
                    label = { Text("Student Name *") },
                    isError = isError,
                    supportingText = { if (isError) Text("Student name is required") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = { Text("Parent / Guardian Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = parentPhone,
                    onValueChange = { parentPhone = it },
                    label = { Text("Parent Contact Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    placeholder = { Text("e.g. +91 9876543210") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        isError = true
                    } else {
                        onSave(name, parentName, parentPhone)
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// COMPOSABLES: WHATSAPP MESSAGE GENERATOR
// ==========================================
@Composable
fun WhatsAppReportDialog(
    student: Student,
    attendanceList: List<Attendance>,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    val context = LocalContext.current
    val recentMonths = remember { getRecentMonths() }
    var selectedMonthIndex by remember { mutableStateOf(0) }
    val selectedMonthValue = recentMonths[selectedMonthIndex].first
    val selectedMonthLabel = recentMonths[selectedMonthIndex].second

    val studentAttendance = remember(attendanceList, selectedMonthValue, student.id) {
        attendanceList.filter { it.studentId == student.id && it.dateString.startsWith(selectedMonthValue) }
    }

    val presentCount = remember(studentAttendance) { studentAttendance.count { it.isPresent } }
    val totalDays = studentAttendance.size
    val percent = if (totalDays > 0) (presentCount * 100) / totalDays else 0

    // Editable Custom Message Template
    var customMessageText by remember { mutableStateOf("") }

    // Recompute default message whenever month/attendance selection changes
    LaunchedEffect(selectedMonthIndex, studentAttendance) {
        customMessageText = "Hello Parent of *${student.name}* (${student.parentName.ifEmpty { "Guardian" }}),\n\n" +
                "Here is the attendance report for *${student.name}* for the month of *${selectedMonthLabel}*:\n\n" +
                "📅 *Total Classes:* $totalDays\n" +
                "✅ *Present:* $presentCount days\n" +
                "❌ *Absent:* ${totalDays - presentCount} days\n" +
                "📈 *Attendance Rate:* $percent%\n\n" +
                "Thank you!\n" +
                "_Tuition Teacher_"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Monthly Report", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Generate and share attendance reports directly with ${student.name}'s parents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                // Month drop selector
                Text("Select Report Month:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentMonths.forEachIndexed { idx, pair ->
                        FilterChip(
                            selected = selectedMonthIndex == idx,
                            onClick = { selectedMonthIndex = idx },
                            label = { Text(pair.second, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = student.parentPhone,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Parent's Contact Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = customMessageText,
                    onValueChange = { customMessageText = it },
                    label = { Text("Message Body") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Copy Action
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Attendance Report", customMessageText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }

                Button(
                    onClick = { onSend(student.parentPhone, customMessageText) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// UTILITY ENGINE FUNCTIONS
// ==========================================

fun showDatePicker(context: Context, currentDateStr: String, onDateSelected: (String) -> Unit) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = try {
        sdf.parse(currentDateStr) ?: Date()
    } catch (e: Exception) {
        Date()
    }
    val cal = Calendar.getInstance()
    cal.time = date

    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val day = cal.get(Calendar.DAY_OF_MONTH)

    android.app.DatePickerDialog(
        context,
        { _, y, m, d ->
            val selectedCal = Calendar.getInstance()
            selectedCal.set(y, m, d)
            val newDateStr = sdf.format(selectedCal.time)
            onDateSelected(newDateStr)
        },
        year,
        month,
        day
    ).show()
}

fun changeDay(currentDateStr: String, amount: Int): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = try {
        sdf.parse(currentDateStr) ?: Date()
    } catch (e: Exception) {
        Date()
    }
    val cal = Calendar.getInstance()
    cal.time = date
    cal.add(Calendar.DAY_OF_MONTH, amount)
    return sdf.format(cal.time)
}

fun getRecentMonths(): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    val cal = Calendar.getInstance()
    val valueFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val labelFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    for (i in 0 until 6) {
        list.add(Pair(valueFormat.format(cal.time), labelFormat.format(cal.time)))
        cal.add(Calendar.MONTH, -1)
    }
    return list
}

fun shareMonthlyPdf(context: Context, monthLabel: String, students: List<Student>, attendance: List<Attendance>) {
    try {
        val fileName = "Attendance_Report_$monthLabel.pdf"
        val file = File(context.cacheDir, fileName)

        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        
        val monthAttendance = attendance.filter { it.dateString.startsWith(monthLabel) }

        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#312E81")
            textSize = 18f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val subtitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#475569")
            textSize = 10f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        val headerBgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#312E81")
            style = android.graphics.Paint.Style.FILL
        }
        val headerTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 10f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val rowBgEvenPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F8FAFC")
            style = android.graphics.Paint.Style.FILL
        }
        val rowBgOddPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        val textPrimaryPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#0F172A")
            textSize = 9f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val textSecondaryPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#475569")
            textSize = 8f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        val textPercentagePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#0D9488")
            textSize = 10f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        val pageNumberPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 8f
            isAntiAlias = true
        }

        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        canvas.drawText("Tuition Attendance Report", 40f, 60f, titlePaint)
        
        val displayMonth = try {
            val inputFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(monthLabel)
            if (date != null) outputFormat.format(date) else monthLabel
        } catch (e: Exception) {
            monthLabel
        }
        canvas.drawText("Month: $displayMonth  |  Generated on ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}", 40f, 78f, subtitlePaint)
        canvas.drawLine(40f, 90f, (pageWidth - 40).toFloat(), 90f, borderPaint)

        val tableTop = 110f
        val rowHeight = 35f
        val headerHeight = 25f

        val leftMargin = 40f
        val rightMargin = (pageWidth - 40).toFloat()
        
        canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + headerHeight, headerBgPaint)
        canvas.drawText("STUDENT NAME", leftMargin + 10f, tableTop + 16f, headerTextPaint)
        canvas.drawText("PARENT CONTACT", leftMargin + 160f, tableTop + 16f, headerTextPaint)
        canvas.drawText("ATTENDANCE (P/A/T)", leftMargin + 320f, tableTop + 16f, headerTextPaint)
        canvas.drawText("RATE %", leftMargin + 450f, tableTop + 16f, headerTextPaint)

        var currentY = tableTop + headerHeight
        val itemsPerPage = 17

        students.forEachIndexed { index, student ->
            val relativeIndex = index % itemsPerPage
            if (index > 0 && relativeIndex == 0) {
                canvas.drawText("Page $pageNumber", (pageWidth - 80).toFloat(), (pageHeight - 30).toFloat(), pageNumberPaint)
                pdfDocument.finishPage(page)
                
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                canvas.drawText("Tuition Attendance Report - $displayMonth (Cont.)", 40f, 50f, subtitlePaint)
                canvas.drawLine(40f, 60f, (pageWidth - 40).toFloat(), 60f, borderPaint)

                val subTableTop = 75f
                canvas.drawRect(leftMargin, subTableTop, rightMargin, subTableTop + headerHeight, headerBgPaint)
                canvas.drawText("STUDENT NAME", leftMargin + 10f, subTableTop + 16f, headerTextPaint)
                canvas.drawText("PARENT CONTACT", leftMargin + 160f, subTableTop + 16f, headerTextPaint)
                canvas.drawText("ATTENDANCE (P/A/T)", leftMargin + 320f, subTableTop + 16f, headerTextPaint)
                canvas.drawText("RATE %", leftMargin + 450f, subTableTop + 16f, headerTextPaint)

                currentY = subTableTop + headerHeight
            }

            val studentAttendance = monthAttendance.filter { it.studentId == student.id }
            val presentCount = studentAttendance.count { it.isPresent }
            val totalDays = studentAttendance.size
            val absentCount = totalDays - presentCount
            val percent = if (totalDays > 0) (presentCount * 100) / totalDays else 0

            val rowPaint = if (index % 2 == 0) rowBgEvenPaint else rowBgOddPaint
            canvas.drawRect(leftMargin, currentY, rightMargin, currentY + rowHeight, rowPaint)

            canvas.drawText(student.name, leftMargin + 10f, currentY + 16f, textPrimaryPaint)
            val joinDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(student.joinDate))
            canvas.drawText("Joined: $joinDateStr", leftMargin + 10f, currentY + 28f, textSecondaryPaint)

            val pName = if (student.parentName.isNotEmpty()) student.parentName else "N/A"
            val pPhone = if (student.parentPhone.isNotEmpty()) student.parentPhone else "N/A"
            canvas.drawText(pName, leftMargin + 160f, currentY + 16f, textPrimaryPaint)
            canvas.drawText(pPhone, leftMargin + 160f, currentY + 28f, textSecondaryPaint)

            canvas.drawText("$presentCount Present / $absentCount Absent", leftMargin + 320f, currentY + 16f, textPrimaryPaint)
            canvas.drawText("Total: $totalDays classes", leftMargin + 320f, currentY + 28f, textSecondaryPaint)

            canvas.drawText("$percent%", leftMargin + 450f, currentY + 22f, textPercentagePaint)

            canvas.drawLine(leftMargin, currentY + rowHeight, rightMargin, currentY + rowHeight, borderPaint)

            currentY += rowHeight
        }

        canvas.drawText("Page $pageNumber", (pageWidth - 80).toFloat(), (pageHeight - 30).toFloat(), pageNumberPaint)
        pdfDocument.finishPage(page)

        file.outputStream().use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Tuition Attendance Report - $monthLabel")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Attendance Report (PDF)"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareApk(context: Context) {
    try {
        val appInfo = context.applicationInfo
        val apkFile = File(appInfo.sourceDir)
        val cacheFile = File(context.cacheDir, "TuitionAttendance.apk")

        apkFile.inputStream().use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cacheFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Tuition App (APK)"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing APK: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareViaWhatsApp(context: Context, phone: String, message: String) {
    try {
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Attendance Message", message)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "WhatsApp not installed. Copied to clipboard!", Toast.LENGTH_LONG).show()
    }
}

data class ChartPoint(
    val dateString: String,
    val displayLabel: String,
    val percentage: Float,
    val presentCount: Int,
    val totalCount: Int
)

@Composable
fun ClassAttendanceTrendChart(
    chartData: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Class Attendance Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Daily attendance rate over the month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Timeline,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (chartData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = labelColor,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No classes recorded for this month yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = labelColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val paddingLeft = 120f
                    val paddingRight = 40f
                    val paddingTop = 50f
                    val paddingBottom = 80f
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(chartData) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val width = size.width
                                        val chartWidth = width - paddingLeft - paddingRight
                                        if (chartData.size > 1 && chartWidth > 0) {
                                            val xSegment = chartWidth / (chartData.size - 1)
                                            val touchX = offset.x - paddingLeft
                                            val index = (touchX / xSegment).roundToInt().coerceIn(0, chartData.size - 1)
                                            selectedPointIndex = if (selectedPointIndex == index) null else index
                                        } else if (chartData.size == 1) {
                                            selectedPointIndex = if (selectedPointIndex == 0) null else 0
                                        }
                                    }
                                )
                            }
                    ) {
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val chartWidth = width - paddingLeft - paddingRight
                        val chartHeight = height - paddingTop - paddingBottom
                        
                        if (chartWidth > 0 && chartHeight > 0) {
                            // Draw Grid Lines (Y-Axis references: 0%, 25%, 50%, 75%, 100%)
                            val gridRatios = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                            gridRatios.forEach { ratio ->
                                val y = paddingTop + chartHeight - ratio * chartHeight
                                
                                // Horizontal grid line
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(paddingLeft, y),
                                    end = Offset(paddingLeft + chartWidth, y),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                )
                                
                                // Y-Axis label
                                val percentageLabel = "${(ratio * 100).toInt()}%"
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = percentageLabel,
                                    topLeft = Offset(20f, y - 16f),
                                    style = TextStyle(color = labelColor, fontSize = 9.sp)
                                )
                            }
                            
                            // Calculate coordinates of data points
                            val points = chartData.mapIndexed { index, point ->
                                val x = if (chartData.size > 1) {
                                    paddingLeft + (index.toFloat() / (chartData.size - 1)) * chartWidth
                                } else {
                                    paddingLeft + chartWidth / 2f
                                }
                                val y = paddingTop + chartHeight - (point.percentage / 100f) * chartHeight
                                Offset(x, y)
                            }
                            
                            // 1. Draw Area Gradient under the line (Recharts style Area)
                            if (points.isNotEmpty()) {
                                val fillPath = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    for (i in 1 until points.size) {
                                        lineTo(points[i].x, points[i].y)
                                    }
                                    // Close the path at the bottom
                                    lineTo(points.last().x, paddingTop + chartHeight)
                                    lineTo(points.first().x, paddingTop + chartHeight)
                                    close()
                                }
                                
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = 0.25f),
                                            primaryColor.copy(alpha = 0.0f)
                                        ),
                                        startY = paddingTop,
                                        endY = paddingTop + chartHeight
                                    )
                                )
                            }
                            
                            // 2. Draw Trend Line (thick primary stroke)
                            if (points.size > 1) {
                                val strokePath = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    for (i in 1 until points.size) {
                                        lineTo(points[i].x, points[i].y)
                                    }
                                }
                                drawPath(
                                    path = strokePath,
                                    color = primaryColor,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            } else if (points.size == 1) {
                                // Only 1 point, draw a solid line across
                                drawLine(
                                    color = primaryColor,
                                    start = Offset(paddingLeft, points[0].y),
                                    end = Offset(paddingLeft + chartWidth, points[0].y),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            
                            // 3. Draw Dots on each active point
                            points.forEachIndexed { index, offset ->
                                val isSelected = selectedPointIndex == index
                                
                                // Outer ring for selected point
                                if (isSelected) {
                                    drawCircle(
                                        color = primaryColor.copy(alpha = 0.3f),
                                        radius = 7.dp.toPx(),
                                        center = offset
                                    )
                                }
                                
                                // Main dot
                                drawCircle(
                                    color = if (isSelected) primaryColor else surfaceColor,
                                    radius = 4.dp.toPx(),
                                    center = offset
                                )
                                drawCircle(
                                    color = primaryColor,
                                    radius = 4.dp.toPx(),
                                    center = offset,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                            
                            // 4. Draw X-Axis Date Labels at the bottom
                            val labelStep = if (chartData.size > 8) chartData.size / 4 else 1
                            chartData.forEachIndexed { index, point ->
                                if (index % labelStep == 0 || index == chartData.size - 1) {
                                    val offset = points[index]
                                    val labelText = point.displayLabel
                                    val textLayoutResult = textMeasurer.measure(labelText)
                                    drawText(
                                        textLayoutResult = textLayoutResult,
                                        topLeft = Offset(offset.x - textLayoutResult.size.width / 2f, paddingTop + chartHeight + 12f),
                                        color = labelColor
                                    )
                                }
                            }
                        }
                    }
                    
                    // 5. Beautiful Interactive Tooltip overlay matching Recharts Card
                    selectedPointIndex?.let { index ->
                        if (index < chartData.size) {
                            val point = chartData[index]
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 50.dp, end = 15.dp, top = 10.dp, bottom = 30.dp)
                            ) {
                                val percentageX = if (chartData.size > 1) {
                                    index.toFloat() / (chartData.size - 1)
                                } else 0.5f
                                
                                val alignOffset = if (percentageX > 0.5f) {
                                    Alignment.TopStart
                                } else {
                                    Alignment.TopEnd
                                }
                                
                                Card(
                                    modifier = Modifier
                                        .align(alignOffset)
                                        .width(140.dp)
                                        .padding(4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val displayDay = try {
                                                val dateObj = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(point.dateString)
                                                if (dateObj != null) SimpleDateFormat("MMM d", Locale.getDefault()).format(dateObj) else point.dateString
                                            } catch (e: Exception) {
                                                point.dateString
                                            }
                                            Text(
                                                text = displayDay,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            IconButton(
                                                onClick = { selectedPointIndex = null },
                                                modifier = Modifier.size(14.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Close tooltip",
                                                    modifier = Modifier.size(10.dp),
                                                    tint = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(primaryColor)
                                            )
                                            Text(
                                                text = "Rate: ${String.format("%.1f%%", point.percentage)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Ratio: ${point.presentCount}/${point.totalCount} present",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Legend
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp, 3.dp)
                        .background(primaryColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Class Attendance Rate (%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
