package com.example.data

import kotlinx.coroutines.flow.Flow

class AttendanceRepository(private val db: AppDatabase) {
    private val studentDao = db.studentDao()
    private val attendanceDao = db.attendanceDao()

    val allStudents: Flow<List<Student>> = studentDao.getAllStudents()
    val allAttendance: Flow<List<Attendance>> = attendanceDao.getAllAttendance()

    fun getAttendanceForDate(dateString: String): Flow<List<Attendance>> {
        return attendanceDao.getAttendanceForDate(dateString)
    }

    suspend fun insertStudent(student: Student): Long {
        return studentDao.insertStudent(student)
    }

    suspend fun updateStudent(student: Student) {
        studentDao.updateStudent(student)
    }

    suspend fun deleteStudent(student: Student) {
        attendanceDao.deleteAttendanceForStudent(student.id)
        studentDao.deleteStudent(student)
    }

    suspend fun saveAttendance(attendance: Attendance) {
        attendanceDao.insertAttendance(attendance)
    }

    suspend fun saveAttendanceList(attendanceList: List<Attendance>) {
        attendanceDao.insertAttendanceList(attendanceList)
    }
}
