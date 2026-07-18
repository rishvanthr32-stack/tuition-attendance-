package com.example.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "attendance",
    primaryKeys = ["studentId", "dateString"],
    indices = [Index(value = ["dateString"])]
)
data class Attendance(
    val studentId: Long,
    val dateString: String, // format "yyyy-MM-dd"
    val isPresent: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
