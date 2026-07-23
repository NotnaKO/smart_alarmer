package com.example.smartalarmer.alarm

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmSoundResolverTest {
    @Test
    fun playbackCandidates_resolvesStoredDefaultToAlarmAndKeepsCallFallbackLast() {
        val defaults = FakeDefaultRingtoneUriProvider()

        val candidates =
            AlarmSoundResolver.playbackCandidates(
                selectedUri = Settings.System.DEFAULT_RINGTONE_URI,
                defaults = defaults
            )

        assertEquals(
            listOf(
                defaults.actualAlarm,
                defaults.symbolicAlarm,
                defaults.actualNotification,
                defaults.symbolicNotification,
                defaults.actualCall,
                defaults.symbolicCall
            ),
            candidates
        )
    }

    @Test
    fun playbackCandidates_keepsExplicitCustomSoundFirstAndRemovesDuplicates() {
        val defaults = FakeDefaultRingtoneUriProvider()
        val custom = Uri.parse("content://test/custom-alarm")

        val candidates = AlarmSoundResolver.playbackCandidates(custom, defaults)

        assertEquals(custom, candidates.first())
        assertEquals(defaults.symbolicCall, candidates.last())
        assertEquals(candidates.distinct(), candidates)
    }

    @Test
    fun pickerIntent_isAlarmOnlyAndUsesAlarmDefault() {
        val intent =
            AlarmSoundResolver.pickerIntent(
                title = "Alarm sound",
                selectedUri = Settings.System.DEFAULT_RINGTONE_URI
            )

        assertEquals(
            RingtoneManager.TYPE_ALARM,
            intent.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, -1)
        )
        assertEquals(
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            intent.getParcelableExtraCompat(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI)
        )
        assertEquals(
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            intent.getParcelableExtraCompat(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI)
        )
    }

    @Test
    fun pickerIntent_marksAlarmDefaultAsExistingWhenNoSoundWasSelected() {
        val intent = AlarmSoundResolver.pickerIntent(title = "Alarm sound", selectedUri = null)

        assertEquals(
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            intent.getParcelableExtraCompat(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI)
        )
    }

    @Test
    fun persistableReadMode_requiresReadAndPersistableGrants() {
        assertNull(AlarmSoundResolver.persistableReadMode(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertNull(AlarmSoundResolver.persistableReadMode(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            AlarmSoundResolver.persistableReadMode(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(name: String): Uri? = getParcelableExtra(name)

    private class FakeDefaultRingtoneUriProvider : DefaultRingtoneUriProvider {
        val actualAlarm: Uri = Uri.parse("content://test/actual-alarm")
        val symbolicAlarm: Uri = Settings.System.DEFAULT_ALARM_ALERT_URI
        val actualNotification: Uri = Uri.parse("content://test/actual-notification")
        val symbolicNotification: Uri = Settings.System.DEFAULT_NOTIFICATION_URI
        val actualCall: Uri = Uri.parse("content://test/actual-call")
        val symbolicCall: Uri = Settings.System.DEFAULT_RINGTONE_URI

        override fun actualUri(type: Int): Uri? = when (type) {
            RingtoneManager.TYPE_ALARM -> actualAlarm
            RingtoneManager.TYPE_NOTIFICATION -> actualNotification
            RingtoneManager.TYPE_RINGTONE -> actualCall
            else -> null
        }

        override fun symbolicUri(type: Int): Uri? = when (type) {
            RingtoneManager.TYPE_ALARM -> symbolicAlarm
            RingtoneManager.TYPE_NOTIFICATION -> symbolicNotification
            RingtoneManager.TYPE_RINGTONE -> symbolicCall
            else -> null
        }
    }
}
