package com.example.smartalarmer.alarm

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal interface DefaultRingtoneUriProvider {
    fun actualUri(type: Int): Uri?

    fun symbolicUri(type: Int): Uri?
}

internal object AlarmSoundResolver {
    fun playbackCandidates(
        context: Context,
        selectedUri: Uri?
    ): List<Uri> = playbackCandidates(selectedUri, AndroidDefaultRingtoneUriProvider(context))

    internal fun playbackCandidates(
        selectedUri: Uri?,
        defaults: DefaultRingtoneUriProvider
    ): List<Uri> {
        val selectedCandidate =
            selectedUri?.let { uri ->
                if (RingtoneManager.getDefaultType(uri) != -1) {
                    defaults.actualUri(RingtoneManager.TYPE_ALARM)
                        ?: defaults.symbolicUri(RingtoneManager.TYPE_ALARM)
                } else {
                    uri
                }
            }

        return listOfNotNull(
            selectedCandidate,
            defaults.actualUri(RingtoneManager.TYPE_ALARM),
            defaults.symbolicUri(RingtoneManager.TYPE_ALARM),
            defaults.actualUri(RingtoneManager.TYPE_NOTIFICATION),
            defaults.symbolicUri(RingtoneManager.TYPE_NOTIFICATION),
            defaults.actualUri(RingtoneManager.TYPE_RINGTONE),
            defaults.symbolicUri(RingtoneManager.TYPE_RINGTONE)
        ).distinct()
    }

    fun pickerIntent(
        title: String,
        selectedUri: Uri?
    ): Intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
            Settings.System.DEFAULT_ALARM_ALERT_URI
        )
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            selectedUri?.let(::normalizePickerSelection)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        )
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }

    fun readPickerSelection(data: Intent?): Uri? {
        val selectedUri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
        return selectedUri?.let(::normalizePickerSelection)
    }

    fun retainReadAccessIfOffered(
        context: Context,
        data: Intent?,
        uri: Uri
    ) {
        val modeFlags = persistableReadMode(data?.flags ?: 0) ?: return
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, modeFlags)
        }.onFailure { error ->
            android.util.Log.w(TAG, "Ringtone URI did not offer persistable read access: $uri", error)
        }
    }

    internal fun persistableReadMode(intentFlags: Int): Int? {
        val hasReadGrant = intentFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        val isPersistable = intentFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        return if (hasReadGrant && isPersistable) Intent.FLAG_GRANT_READ_URI_PERMISSION else null
    }

    private fun normalizePickerSelection(uri: Uri): Uri = if (RingtoneManager.getDefaultType(uri) != -1) {
        Settings.System.DEFAULT_ALARM_ALERT_URI
    } else {
        uri
    }

    private class AndroidDefaultRingtoneUriProvider(
        private val context: Context
    ) : DefaultRingtoneUriProvider {
        override fun actualUri(type: Int): Uri? = runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, type) }.getOrNull()

        override fun symbolicUri(type: Int): Uri? = RingtoneManager.getDefaultUri(type)
    }

    private const val TAG = "AlarmSoundResolver"
}
