package com.example.smartalarmer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String, // CSV e.g., "1,2,3,4,5"
    val isEnabled: Boolean = true,
    val puzzlesList: String, // CSV e.g., "MATH,TYPING,MEMORY"
    val puzzleCount: Int = 2,
    val isGradualVolume: Boolean = true,
    val label: String = "",
    val soundUri: String? = null
)
