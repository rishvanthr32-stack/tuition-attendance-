package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentName: String = "",
    val parentPhone: String = "", // Used for WhatsApp sharing
    val joinDate: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
