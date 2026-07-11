# Suggested Google Play declarations

These answers describe the current repository. Re-check them whenever a
dependency, permission, or feature changes.

## App content

- Ads: **No**
- App access: **All functionality is available without special access**
- Data safety — data collected: **No**
- Data safety — data shared: **No**
- Accounts: **The app does not support account creation**
- News app: **No**
- Government app: **No**
- Health features: **No**
- Category: **App / Tools**
- Privacy-policy URL after GitHub Pages is enabled:
  `https://notnako.github.io/smart_alarmer/privacy-policy.html`

The target-audience and content-rating answers must reflect the audience chosen
by the developer. The app contains no ads, purchases, user-generated content,
violence, gambling, sexual content, or unrestricted Internet access.

## Foreground service: media playback

**Functionality:** When a user-created alarm fires, Smart Alarmer starts a
foreground service that plays the selected alarm sound and posts an ongoing
notification until the configured puzzle sequence is completed.

**Why immediate execution is necessary:** Deferring the service would cause a
time-critical alarm to sound late. Interrupting it could silence an active alarm
before the user wakes and completes the dismissal tasks.

**Suggested demonstration video:** Record a physical device while you create an
alarm one or two minutes ahead, lock the device, let the alarm trigger, show the
foreground notification and full-screen puzzle activity, then complete the
puzzles and show that the sound and notification stop. Do not use preview mode
for this policy video because the video must demonstrate the foreground service.

## Full-screen intent

**Core functionality:** Alarm clock. Users explicitly schedule time-critical
alarms. When an alarm fires, the full-screen intent presents the dismissal
puzzles over the lock screen so the user can see and silence the alarm.

## Exact alarms

The app declares the user-granted `SCHEDULE_EXACT_ALARM` permission and calls
`AlarmManager.setAlarmClock()` for alarms explicitly created by the user. It does
not declare the restricted `USE_EXACT_ALARM` permission.

## Permissions summary

| Permission | User-facing purpose |
| --- | --- |
| `POST_NOTIFICATIONS` | Show an active-alarm foreground notification. |
| `SCHEDULE_EXACT_ALARM` | Ring alarms at the time selected by the user. |
| `USE_FULL_SCREEN_INTENT` | Present the ringing alarm over the lock screen. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Continue alarm audio while the app is not visible. |
| `WAKE_LOCK` | Keep alarm processing active while the alarm rings. |
| `RECEIVE_BOOT_COMPLETED` | Restore enabled alarms after a reboot or app update. |
| `MODIFY_AUDIO_SETTINGS` | Apply and restore alarm volume behavior. |
