package com.example.smartalarmer.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.R
import com.example.smartalarmer.ui.theme.OrangeWarning
import com.example.smartalarmer.ui.theme.OrangeWarningSemi
import com.example.smartalarmer.ui.theme.RedError
import com.example.smartalarmer.ui.theme.RedErrorSemi
import com.example.smartalarmer.ui.theme.SecondaryText
import com.example.smartalarmer.utils.AlarmCapabilityState

@Composable
internal fun XiaomiExecutionWarningCard(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    SetupWarningCard(
        borderColor = OrangeWarning,
        containerColor = OrangeWarningSemi,
        title = stringResource(R.string.bg_execution_settings),
        description = stringResource(R.string.bg_execution_desc)
    ) {
        SetupButton(
            text = stringResource(R.string.xiaomi_settings),
            color = OrangeWarning,
            contentColor = Color.Black,
            onClick = onOpenSettings
        )
        OutlinedButton(
            onClick = onDismiss,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeWarning),
            border = BorderStroke(1.dp, OrangeWarning.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(stringResource(R.string.dismiss), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun AlarmCapabilityWarningCard(
    capabilities: AlarmCapabilityState,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onRequestFullScreenAccess: () -> Unit
) {
    SetupWarningCard(
        borderColor = RedError,
        containerColor = RedErrorSemi,
        title = stringResource(R.string.permissions_required),
        description = stringResource(R.string.permissions_desc)
    ) {
        if (!capabilities.notificationPermissionGranted) {
            SetupButton(
                text = stringResource(R.string.allow_notifications),
                color = RedError,
                onClick = onRequestNotifications
            )
        }
        if (capabilities.notificationPermissionGranted &&
            (!capabilities.notificationsEnabled || !capabilities.alarmChannelUsable)
        ) {
            SetupButton(
                text = stringResource(R.string.notification_settings),
                color = RedError,
                onClick = onOpenNotificationSettings
            )
        }
        if (!capabilities.exactAlarmAccess) {
            SetupButton(
                text = stringResource(R.string.allow_alarms),
                color = RedError,
                onClick = onRequestExactAlarmAccess
            )
        }
        if (!capabilities.fullScreenIntentAccess) {
            SetupButton(
                text = stringResource(R.string.allow_lockscreen),
                color = RedError,
                onClick = onRequestFullScreenAccess
            )
        }
    }
}

@Composable
private fun SetupWarningCard(
    borderColor: Color,
    containerColor: Color,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = SecondaryText, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SetupButton(
    text: String,
    color: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp)
    }
}
