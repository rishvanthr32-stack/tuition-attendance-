package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Attendance
import com.example.data.AttendanceRepository
import com.example.data.Student
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class SyncState {
    IDLE,
    SYNCING,
    SYNCED,
    OFFLINE_SAVING
}

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AttendanceRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AttendanceRepository(db)
    }

    // Date State (format: yyyy-MM-dd)
    private val _selectedDate = MutableStateFlow(getCurrentDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Students List
    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All Attendance Records (for monthly stats)
    val allAttendance: StateFlow<List<Attendance>> = repository.allAttendance
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Map of studentId -> Attendance for the selected date
    val attendanceMap: StateFlow<Map<Long, Attendance>> = _selectedDate
        .flatMapLatest { date ->
            repository.getAttendanceForDate(date)
        }
        .map { list -> list.associateBy { it.studentId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // Network & Sync State
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState.OFFLINE_SAVING)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        monitorNetwork()
    }

    private fun monitorNetwork() {
        // Initial state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val initialOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _isOnline.value = initialOnline
        _syncState.value = if (initialOnline) SyncState.SYNCED else SyncState.OFFLINE_SAVING

        // Register callback
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    triggerSync()
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                    _syncState.value = SyncState.OFFLINE_SAVING
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerSync() {
        if (!_isOnline.value) return
        viewModelScope.launch {
            _syncState.value = SyncState.SYNCING
            // Simulate cloud DB automatic synchronization payload
            delay(1500)
            _syncState.value = SyncState.SYNCED
        }
    }

    fun setDate(dateStr: String) {
        _selectedDate.value = dateStr
    }

    fun addStudent(name: String, parentName: String, parentPhone: String) {
        viewModelScope.launch {
            val student = Student(
                name = name.trim(),
                parentName = parentName.trim(),
                parentPhone = parentPhone.trim()
            )
            repository.insertStudent(student)
            triggerSync()
        }
    }

    fun updateStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student)
            triggerSync()
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            triggerSync()
        }
    }

    fun saveAttendance(studentId: Long, isPresent: Boolean) {
        viewModelScope.launch {
            val attendance = Attendance(
                studentId = studentId,
                dateString = _selectedDate.value,
                isPresent = isPresent
            )
            repository.saveAttendance(attendance)
            triggerSync()
        }
    }

    fun markAllPresent() {
        viewModelScope.launch {
            val list = students.value.map { student ->
                Attendance(
                    studentId = student.id,
                    dateString = _selectedDate.value,
                    isPresent = true
                )
            }
            repository.saveAttendanceList(list)
            triggerSync()
        }
    }

    fun markAllAbsent() {
        viewModelScope.launch {
            val list = students.value.map { student ->
                Attendance(
                    studentId = student.id,
                    dateString = _selectedDate.value,
                    isPresent = false
                )
            }
            repository.saveAttendanceList(list)
            triggerSync()
        }
    }

    fun markSelectedAttendance(studentIds: List<Long>, isPresent: Boolean) {
        viewModelScope.launch {
            val list = studentIds.map { id ->
                Attendance(
                    studentId = id,
                    dateString = _selectedDate.value,
                    isPresent = isPresent
                )
            }
            repository.saveAttendanceList(list)
            triggerSync()
        }
    }

    fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun exportBackupJson(): String {
        return try {
            val root = JSONObject()
            
            val studentsArray = JSONArray()
            students.value.forEach { s ->
                val sObj = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("parentName", s.parentName)
                    put("parentPhone", s.parentPhone)
                    put("joinDate", s.joinDate)
                    put("isActive", s.isActive)
                }
                studentsArray.put(sObj)
            }
            root.put("students", studentsArray)

            val attendanceArray = JSONArray()
            allAttendance.value.forEach { a ->
                val aObj = JSONObject().apply {
                    put("studentId", a.studentId)
                    put("dateString", a.dateString)
                    put("isPresent", a.isPresent)
                    put("timestamp", a.timestamp)
                }
                attendanceArray.put(aObj)
            }
            root.put("attendance", attendanceArray)
            
            root.toString(4)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun importBackupJson(jsonString: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonString)
                val studentsArray = root.optJSONArray("students")
                val attendanceArray = root.optJSONArray("attendance")

                if (studentsArray == null && attendanceArray == null) {
                    onError("Invalid backup file: no students or attendance data found.")
                    return@launch
                }

                // First, restore students
                if (studentsArray != null) {
                    for (i in 0 until studentsArray.length()) {
                        val obj = studentsArray.getJSONObject(i)
                        val id = obj.optLong("id", 0)
                        val name = obj.optString("name", "")
                        val parentName = obj.optString("parentName", "")
                        val parentPhone = obj.optString("parentPhone", "")
                        val joinDate = obj.optLong("joinDate", System.currentTimeMillis())
                        val isActive = obj.optBoolean("isActive", true)

                        if (name.isNotEmpty()) {
                            val student = Student(
                                id = id,
                                name = name,
                                parentName = parentName,
                                parentPhone = parentPhone,
                                joinDate = joinDate,
                                isActive = isActive
                            )
                            repository.insertStudent(student)
                        }
                    }
                }

                // Next, restore attendance records
                if (attendanceArray != null) {
                    val attendanceList = mutableListOf<Attendance>()
                    for (i in 0 until attendanceArray.length()) {
                        val obj = attendanceArray.getJSONObject(i)
                        val studentId = obj.optLong("studentId", 0)
                        val dateString = obj.optString("dateString", "")
                        val isPresent = obj.optBoolean("isPresent", true)
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

                        if (studentId > 0 && dateString.isNotEmpty()) {
                            attendanceList.add(
                                Attendance(
                                    studentId = studentId,
                                    dateString = dateString,
                                    isPresent = isPresent,
                                    timestamp = timestamp
                                )
                            )
                        }
                    }
                    if (attendanceList.isNotEmpty()) {
                        repository.saveAttendanceList(attendanceList)
                    }
                }

                triggerSync()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError("Failed to parse backup file: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }
}
