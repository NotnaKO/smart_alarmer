# Pause Alarm Through Date Design

## Goal

Let users pause a recurring alarm across several occurrences without turning
off its repeating schedule or repeatedly choosing **Skip this occurrence**.

## User experience

The recurring-alarm toggle dialog adds **Pause through date** between the
one-occurrence skip and complete-disable actions. It opens a localized date
picker whose initial and earliest selectable date is the alarm's currently
scheduled local occurrence.

Confirming keeps the alarm enabled, suppresses every matching occurrence
through the selected date, and displays the normal card pause marker and
**Resume schedule** action. The confirmation reports the newly scheduled alarm.
One-time alarms continue to offer only complete disablement.

Pausing cancels any active wake-up checks for the alarm, matching skip and
disable behavior.

## Scheduling and persistence

The selected date is stored in the existing
`Alarm.suppressedThroughEpochDay` field. `AlarmTimeCalculator` schedules the
first matching day after it, including alternating-week and daylight-saving
rules. No database migration or new direct-boot format is required.

The ViewModel resolves the alarm's current trigger using the system zone when
the action runs. It also clamps direct callers to the current occurrence date,
so stale or invalid UI input cannot move suppression before the scheduled
alarm.

## Verification

- Unit tests cover selected-date persistence, replacement scheduling,
  wake-up-check cancellation, action-time zone conversion, and lower-bound
  clamping.
- Compose tests cover date confirmation and the complete dialog-to-picker flow
  in English, German, Spanish, and Russian.
- Existing calculator, card-ordering, direct-boot, and resume tests exercise the
  shared suppression path.
