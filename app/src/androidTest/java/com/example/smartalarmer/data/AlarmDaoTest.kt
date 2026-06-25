package com.example.smartalarmer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDaoTest {

    private lateinit var database: AlarmDatabase
    private lateinit var dao: AlarmDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.alarmDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private fun alarm(
        hour: Int = 7,
        minute: Int = 30,
        isEnabled: Boolean = true,
        daysOfWeek: String = "1,2,3,4,5",
        isGradualVolume: Boolean = true,
        label: String = "Wake Up",
        soundUri: String? = "content://settings/system/alarm_alert"
    ) = Alarm(
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek,
        isEnabled = isEnabled,
        puzzlesList = "MATH",
        puzzleCount = 1,
        isGradualVolume = isGradualVolume,
        label = label,
        soundUri = soundUri
    )

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun insertAndGetAlarm() = runTest {
        val id = dao.insertAlarm(alarm()).toInt()

        val alarms = dao.getAllAlarms().first()
        assertEquals(1, alarms.size)
        assertEquals(id, alarms[0].id)
        assertEquals(7, alarms[0].hour)
        assertEquals(30, alarms[0].minute)
        assertTrue(alarms[0].isGradualVolume)
    }

    @Test
    fun insertAndGetAlarm_withLabelAndSoundUri() = runTest {
        val id = dao.insertAlarm(alarm(label = "Cardio", soundUri = "content://custom/sound")).toInt()
        val alarms = dao.getAllAlarms().first()
        assertEquals(1, alarms.size)
        assertEquals("Cardio", alarms[0].label)
        assertEquals("content://custom/sound", alarms[0].soundUri)
    }

    @Test
    fun insertAndGetAlarm_withGradualVolumeFalse() = runTest {
        dao.insertAlarm(alarm(isGradualVolume = false))
        val alarms = dao.getAllAlarms().first()
        assertEquals(1, alarms.size)
        assertFalse(alarms[0].isGradualVolume)
    }

    @Test
    fun updateAlarm_changesIsEnabled() = runTest {
        val id = dao.insertAlarm(alarm(isEnabled = true)).toInt()
        val inserted = dao.getAllAlarms().first().first()

        dao.updateAlarm(inserted.copy(isEnabled = false))

        val updated = dao.getAllAlarms().first().first()
        assertFalse(updated.isEnabled)
    }

    @Test
    fun deleteAlarm_removesFromDb() = runTest {
        val id = dao.insertAlarm(alarm()).toInt()
        val inserted = dao.getAllAlarms().first().first()

        dao.deleteAlarm(inserted)

        val remaining = dao.getAllAlarms().first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun getEnabledAlarms_onlyReturnsEnabled() = runTest {
        dao.insertAlarm(alarm(isEnabled = true))
        dao.insertAlarm(alarm(hour = 8, isEnabled = false))
        dao.insertAlarm(alarm(hour = 9, isEnabled = true))

        val enabled = dao.getEnabledAlarms()
        assertEquals(2, enabled.size)
        assertTrue(enabled.all { it.isEnabled })
    }

    @Test
    fun multipleAlarms_orderedByHourThenMinute() = runTest {
        dao.insertAlarm(alarm(hour = 9, minute = 0))
        dao.insertAlarm(alarm(hour = 7, minute = 45))
        dao.insertAlarm(alarm(hour = 7, minute = 15))

        val alarms = dao.getAllAlarms().first()
        assertEquals(7, alarms[0].hour)
        assertEquals(15, alarms[0].minute)
        assertEquals(7, alarms[1].hour)
        assertEquals(45, alarms[1].minute)
        assertEquals(9, alarms[2].hour)
    }
}
