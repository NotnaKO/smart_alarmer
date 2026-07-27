# Skip Next Alarm Design

## Goal

Let a user suppress one upcoming occurrence of a recurring alarm without
disabling its repeating schedule. The existing alarm switch is the entry point
so users do not have to discover another card action.

## User experience

Toggling an enabled recurring alarm off opens a compact choice dialog. It shows
the persisted next trigger and offers:

- **Skip this occurrence**: keep the alarm enabled and schedule its following
  occurrence.
- **Turn alarm off**: cancel the main schedule and disable the alarm.
- **Cancel**: leave all state unchanged.

A one-time alarm cannot have a later recurring occurrence, so its dialog offers
only Turn off and Cancel. Skipped cards show the local date through which they
are suppressed and provide **Resume schedule**. Repeating the toggle-and-skip
action extends suppression through the newly displayed next occurrence.

Skipping or disabling an alarm cancels any active wake-up-check session for that
alarm. Resuming the main schedule does not recreate cancelled checks.

## Persistence and scheduling

Room schema version 9 adds nullable `Alarm.suppressedThroughEpochDay`. It stores
the final suppressed local date as `LocalDate.toEpochDay()`, rather than storing
a timezone-sensitive occurrence instant or a transient boolean.

For recurring alarms, `AlarmTimeCalculator` begins its normal day/parity search
after the suppressed date. The existing DST gap and overlap behavior is then
applied to the selected date. A delivered post-suppression occurrence clears the
field before scheduling the following occurrence.

The field is included in the device-protected alarm mirror. Reboot, wall-clock,
and timezone reconciliation therefore retain the local-date suppression.

`AlarmReceiver` compares a main delivery's occurrence timestamp with the
persisted `scheduledTriggerAtMillis`. A cancelled occurrence racing with the
replacement registration is rejected as stale.

## Dashboard ordering

The ViewModel combines alarm and wake-up-check flows into `AlarmCardState`.
Cards with real upcoming events sort by their earliest persisted trigger:

1. Earliest main alarm or active wake-up check.
2. Enabled alarms whose schedule needs attention.
3. Disabled alarms, ordered by configured hour and minute.

Since skipping persists the replacement main trigger, the card immediately
moves to its correct chronological position.

## Future extension

The same suppression field and calculator behavior support a date picker for
**Pause through date** without another schema or scheduling redesign. A
one-occurrence time override remains separate because it replaces rather than
suppresses an occurrence.

## Verification

- Unit tests cover normal and alternating-week suppression, coordinator
  persistence/restore, stale delivery rejection, post-delivery cleanup,
  wake-up-check cancellation, and next-event ordering.
- Instrumented tests cover the version 9 migration, Room persistence,
  direct-boot serialization, card resume action, and the toggle choice dialog.
