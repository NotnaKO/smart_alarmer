package com.example.smartalarmer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AlarmDatabaseTest {
    private lateinit var alarmDao: AlarmDao
    private lateinit var db: AlarmDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java).build()
        alarmDao = db.alarmDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAlarmAndReadInList() = runBlocking {
        val alarm = Alarm(
            hour = 7,
            minute = 30,
            daysOfWeek = "1,2,3,4,5",
            puzzlesList = "MATH,TYPING",
            puzzleCount = 2
        )
        alarmDao.insertAlarm(alarm)
        val allAlarms = alarmDao.getAllAlarms().first()
        assertEquals(1, allAlarms.size)
        assertEquals(7, allAlarms[0].hour)
        assertEquals(30, allAlarms[0].minute)
    }

    @Test
    fun testGetEnabledAlarmsOnly() = runBlocking {
        val alarm1 = Alarm(
            hour = 7,
            minute = 30,
            daysOfWeek = "1,2,3,4,5",
            isEnabled = true,
            puzzlesList = "MATH",
            puzzleCount = 1
        )
        val alarm2 = Alarm(
            hour = 8,
            minute = 0,
            daysOfWeek = "6,7",
            isEnabled = false,
            puzzlesList = "TYPING",
            puzzleCount = 1
        )
        alarmDao.insertAlarm(alarm1)
        alarmDao.insertAlarm(alarm2)
        val enabledAlarms = alarmDao.getEnabledAlarms()
        assertEquals(1, enabledAlarms.size)
        assertEquals(7, enabledAlarms[0].hour)
    }
}
