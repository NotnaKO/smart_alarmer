package com.example.smartalarmer

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.annotation.XmlRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun manifestAllowsOnlyRuleControlledBackup() {
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertTrue(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test
    fun alarmDatabaseIsExcludedFromCloudBackupAndDeviceTransfer() {
        assertTrue(databaseExclusions(R.xml.backup_rules) == 1)
        assertTrue(databaseExclusions(R.xml.data_extraction_rules) == 2)
    }

    private fun databaseExclusions(@XmlRes resourceId: Int): Int {
        val parser = context.resources.getXml(resourceId)
        var exclusions = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (
                parser.eventType == XmlPullParser.START_TAG &&
                parser.name == "exclude" &&
                parser.getAttributeValue(null, "domain") == "database" &&
                parser.getAttributeValue(null, "path") == "."
            ) {
                exclusions++
            }
            parser.next()
        }
        parser.close()
        return exclusions
    }
}
