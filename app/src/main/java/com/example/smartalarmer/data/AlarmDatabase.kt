package com.example.smartalarmer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Alarm::class, WakeUpCheckSession::class], version = 7, exportSchema = true)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun wakeUpCheckDao(): WakeUpCheckDao

    companion object {
        @Volatile private var INSTANCE: AlarmDatabase? = null

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE alarms ADD COLUMN isGradualVolume INTEGER NOT NULL DEFAULT 1")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE alarms ADD COLUMN label TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE alarms ADD COLUMN soundUri TEXT DEFAULT NULL")
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE alarms ADD COLUMN volumeRampSeconds INTEGER NOT NULL DEFAULT 60")
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE alarms ADD COLUMN scheduleStatus TEXT NOT NULL DEFAULT 'UNKNOWN'")
                    db.execSQL("ALTER TABLE alarms ADD COLUMN scheduledTriggerAtMillis INTEGER DEFAULT NULL")
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE alarms ADD COLUMN wakeUpChecksEnabled INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE alarms ADD COLUMN wakeUpCheckCount INTEGER NOT NULL DEFAULT 3")
                    db.execSQL("ALTER TABLE alarms ADD COLUMN wakeUpCheckIntervalMinutes INTEGER NOT NULL DEFAULT 5")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS wake_up_check_sessions (
                            alarmId INTEGER NOT NULL,
                            token TEXT NOT NULL,
                            nextCheckNumber INTEGER NOT NULL,
                            totalChecks INTEGER NOT NULL,
                            intervalMinutes INTEGER NOT NULL,
                            nextTriggerAtMillis INTEGER NOT NULL,
                            puzzlesList TEXT NOT NULL,
                            soundUri TEXT,
                            alarmLabel TEXT NOT NULL,
                            PRIMARY KEY(alarmId)
                        )
                        """.trimIndent()
                    )
                }
            }

        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE alarms_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            hour INTEGER NOT NULL,
                            minute INTEGER NOT NULL,
                            daysOfWeek TEXT NOT NULL,
                            isEnabled INTEGER NOT NULL,
                            puzzlesList TEXT NOT NULL,
                            puzzleCount INTEGER NOT NULL,
                            volumeRampSeconds INTEGER NOT NULL,
                            label TEXT NOT NULL,
                            soundUri TEXT,
                            wakeUpChecksEnabled INTEGER NOT NULL,
                            wakeUpCheckCount INTEGER NOT NULL,
                            wakeUpCheckIntervalMinutes INTEGER NOT NULL,
                            scheduleStatus TEXT NOT NULL,
                            scheduledTriggerAtMillis INTEGER
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO alarms_new (
                            id, hour, minute, daysOfWeek, isEnabled, puzzlesList, puzzleCount,
                            volumeRampSeconds, label, soundUri, wakeUpChecksEnabled,
                            wakeUpCheckCount, wakeUpCheckIntervalMinutes, scheduleStatus,
                            scheduledTriggerAtMillis
                        )
                        SELECT
                            id, hour, minute, daysOfWeek, isEnabled, puzzlesList, puzzleCount,
                            volumeRampSeconds, label, soundUri, wakeUpChecksEnabled,
                            wakeUpCheckCount, wakeUpCheckIntervalMinutes, scheduleStatus,
                            scheduledTriggerAtMillis
                        FROM alarms
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE alarms")
                    db.execSQL("ALTER TABLE alarms_new RENAME TO alarms")
                }
            }

        fun getDatabase(context: Context): AlarmDatabase = INSTANCE ?: synchronized(this) {
            val instance =
                Room
                    .databaseBuilder(
                        context.applicationContext,
                        AlarmDatabase::class.java,
                        "alarm_database"
                    ).addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
            INSTANCE = instance
            instance
        }
    }
}
