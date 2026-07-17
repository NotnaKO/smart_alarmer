# Wake-up Checks Design

## Goal

Wake-up checks help users stay awake after completing the main alarm. They are not snoozes: the main puzzle sequence must be completed before the first follow-up is scheduled.

## User experience

- Each alarm has an optional **Wake-up checks** switch, disabled by default.
- Defaults are three checks at five-minute intervals.
- Users can select one to five checks and a 5, 10, or 15 minute interval.
- Each check requires one easy task randomly chosen from the alarm's selected puzzle types.
- The interval begins when the previous task is completed, preventing checks from arriving too close together.
- The dashboard displays configured settings and, while a sequence is active, its remaining count and next trigger.
- Remaining checks can be stopped from the dashboard after confirmation. Editing, disabling, or deleting the alarm also cancels them.

Easy variants use Easy Math, a three-cell memory pattern, a shortened typing phrase, or eight shakes. An unavailable shake sensor falls back to Math. Follow-ups use the selected ringtone and a fixed 30-second volume ramp.

## Persistence and scheduling

Room schema version 6 adds configuration fields to `Alarm` and a `WakeUpCheckSession` entity. The session snapshots the puzzle pool, label, sound, interval, sequence position, next trigger, and a unique token.

Only one follow-up is registered at a time. `WakeUpCheckCoordinator` creates the first session after main dismissal, advances it after verified completion, and removes it after the final check. A distinct `PendingIntent` identity prevents conflicts with the recurring main alarm. Delivery validates the session token and check number to reject stale broadcasts.

Active sessions survive process death, reboot, app replacement, and time changes through `BootReceiver`. A one-time alarm may be disabled after its main delivery while its persisted wake-up-check session remains independently valid.

## Safety and verification

- Preview alarms never create follow-up sessions.
- Real checks retain the full-screen, foreground-service, audio, and back-button protections of the main alarm.
- Database migration coverage verifies version 1 through 6 defaults.
- Unit tests cover creation, completion-relative intervals, stale commands, and final cleanup.
- Instrumented tests cover Room persistence, editor settings, dashboard summaries, and easy dismiss mode.
