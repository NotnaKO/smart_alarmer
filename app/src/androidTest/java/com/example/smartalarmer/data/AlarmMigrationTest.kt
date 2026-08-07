package com.example.smartalarmer.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migratesAlarmFromVersion1ThroughVersion11WithoutLosingData() = runBlocking {
        createVersion1Database()
        migrateAndValidateVersion2()

        val database =
            Room
                .databaseBuilder(context, AlarmDatabase::class.java, TEST_DATABASE)
                .addMigrations(
                    AlarmDatabase.MIGRATION_2_3,
                    AlarmDatabase.MIGRATION_3_4,
                    AlarmDatabase.MIGRATION_4_5,
                    AlarmDatabase.MIGRATION_5_6,
                    AlarmDatabase.MIGRATION_6_7,
                    AlarmDatabase.MIGRATION_7_8,
                    AlarmDatabase.MIGRATION_8_9,
                    AlarmDatabase.MIGRATION_9_10,
                    AlarmDatabase.MIGRATION_10_11
                )
                .build()
        try {
            val alarm = requireNotNull(database.alarmDao().getAlarmById(7))
            assertEquals(6, alarm.hour)
            assertEquals(45, alarm.minute)
            assertEquals("1,3,5", alarm.daysOfWeek)
            assertEquals("EVERY", alarm.weekParity)
            assertEquals("MATH,TYPING", alarm.puzzlesList)
            assertEquals(60, alarm.volumeRampSeconds)
            assertEquals("", alarm.label)
            assertNull(alarm.soundUri)
            assertEquals(AlarmScheduleStatus.UNKNOWN.name, alarm.scheduleStatus)
            assertNull(alarm.scheduledTriggerAtMillis)
            assertEquals(false, alarm.wakeUpChecksEnabled)
            assertEquals(3, alarm.wakeUpCheckCount)
            assertEquals(5, alarm.wakeUpCheckIntervalMinutes)
            assertNull(alarm.suppressedThroughEpochDay)
            assertNull(alarm.oneTimeDateEpochDay)
            assertEquals(emptyList<WakeUpCheckSession>(), database.wakeUpCheckDao().getAllSessions())
            database.openHelper.readableDatabase
                .query("PRAGMA table_info(alarms)")
                .use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    val columnNames = buildList {
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                    assertEquals(false, "isGradualVolume" in columnNames)
                    assertEquals(false, "backupAlarmTimeoutMinutes" in columnNames)
                    assertEquals(false, "backupAlarmRepeatCount" in columnNames)
                }
            database.openHelper.readableDatabase
                .query("PRAGMA table_info(wake_up_check_sessions)")
                .use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    val columnNames = buildList {
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                    assertEquals(true, "volumeRampSeconds" in columnNames)
                }
        } finally {
            database.close()
        }
    }

    @Test
    fun migratesWakeUpCheckSessionsVolumeRampFromAlarmsTableInVersion10To11() = runBlocking {
        openHelper(version = 10, createCallback = { db ->
            db.execSQL(
                """
                CREATE TABLE alarms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    hour INTEGER NOT NULL, minute INTEGER NOT NULL, daysOfWeek TEXT NOT NULL,
                    weekParity TEXT NOT NULL, isEnabled INTEGER NOT NULL, puzzlesList TEXT NOT NULL,
                    puzzleCount INTEGER NOT NULL, volumeRampSeconds INTEGER NOT NULL, label TEXT NOT NULL,
                    soundUri TEXT, wakeUpChecksEnabled INTEGER NOT NULL, wakeUpCheckCount INTEGER NOT NULL,
                    wakeUpCheckIntervalMinutes INTEGER NOT NULL, scheduleStatus TEXT NOT NULL,
                    scheduledTriggerAtMillis INTEGER, suppressedThroughEpochDay INTEGER, oneTimeDateEpochDay INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE wake_up_check_sessions (
                    alarmId INTEGER NOT NULL, token TEXT NOT NULL, nextCheckNumber INTEGER NOT NULL,
                    totalChecks INTEGER NOT NULL, intervalMinutes INTEGER NOT NULL, nextTriggerAtMillis INTEGER NOT NULL,
                    puzzlesList TEXT NOT NULL, soundUri TEXT, alarmLabel TEXT NOT NULL, PRIMARY KEY(alarmId)
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO alarms (id, hour, minute, daysOfWeek, weekParity, isEnabled, puzzlesList, puzzleCount, volumeRampSeconds, label, soundUri, wakeUpChecksEnabled, wakeUpCheckCount, wakeUpCheckIntervalMinutes, scheduleStatus) VALUES (7, 6, 30, '', 'EVERY', 1, 'MATH', 1, 120, 'Test', NULL, 1, 3, 5, 'SCHEDULED')")
            db.execSQL("INSERT INTO wake_up_check_sessions (alarmId, token, nextCheckNumber, totalChecks, intervalMinutes, nextTriggerAtMillis, puzzlesList, soundUri, alarmLabel) VALUES (7, 'tok', 1, 3, 5, 1000, 'MATH', NULL, 'Test')")
        }).useDatabase()

        val helper = openHelper(version = 11, upgradeCallback = { db, oldV, newV ->
            assertEquals(10, oldV)
            assertEquals(11, newV)
            AlarmDatabase.MIGRATION_10_11.migrate(db)
        })
        try {
            helper.writableDatabase.query("SELECT volumeRampSeconds FROM wake_up_check_sessions WHERE alarmId = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals(120, cursor.getInt(cursor.getColumnIndexOrThrow("volumeRampSeconds")))
            }
        } finally {
            helper.close()
        }
    }

    private fun createVersion1Database() {
        openHelper(version = 1, createCallback = { database ->
            database.execSQL(VERSION_1_CREATE_SQL)
            database.execSQL(
                """
                INSERT INTO alarms (
                    id, hour, minute, daysOfWeek, isEnabled, puzzlesList, puzzleCount
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(7, 6, 45, "1,3,5", 1, "MATH,TYPING", 2)
            )
        }).useDatabase()
    }

    private fun migrateAndValidateVersion2() {
        val helper =
            openHelper(version = 2, upgradeCallback = { database, oldVersion, newVersion ->
                assertEquals(1, oldVersion)
                assertEquals(2, newVersion)
                AlarmDatabase.MIGRATION_1_2.migrate(database)
            })
        try {
            helper.writableDatabase.query("SELECT * FROM alarms WHERE id = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals(6, cursor.getInt(cursor.getColumnIndexOrThrow("hour")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isGradualVolume")))
            }
        } finally {
            helper.close()
        }
    }

    private fun openHelper(
        version: Int,
        createCallback: (SupportSQLiteDatabase) -> Unit = {},
        upgradeCallback: (SupportSQLiteDatabase, Int, Int) -> Unit = { _, _, _ -> }
    ): SupportSQLiteOpenHelper {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(TEST_DATABASE)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = createCallback(db)

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            upgradeCallback(db, oldVersion, newVersion)
                        }
                    }
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun SupportSQLiteOpenHelper.useDatabase() {
        try {
            writableDatabase
        } finally {
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "alarm-migration-test"
        const val VERSION_1_CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS alarms (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                hour INTEGER NOT NULL,
                minute INTEGER NOT NULL,
                daysOfWeek TEXT NOT NULL,
                isEnabled INTEGER NOT NULL,
                puzzlesList TEXT NOT NULL,
                puzzleCount INTEGER NOT NULL
            )
        """
    }
}
