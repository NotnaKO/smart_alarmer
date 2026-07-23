# Alarm Reliability: Direct Boot, Backup Escalation, and Overlap Queue

## Goal

Strengthen alarm delivery around three failure modes: a reboot before the first
unlock, an alarm that receives no verified puzzle progress, and another alarm
firing while the first is still being solved.

## Direct Boot

Room remains credential-protected. Successful main-alarm schedules are mirrored
to device-protected preferences with their trigger time and complete delivery
configuration. `LOCKED_BOOT_COMPLETED` restores those exact registrations before
Room can be opened. If the stored trigger passed while the device was off, it is
delivered immediately after boot rather than silently moved to the next day.
Clock and time-zone changes received before unlock instead recalculate each
trigger from the mirrored local-time configuration.

The receiver, alarm service, and dismiss activity are direct-boot-aware. Custom
sounds that are unavailable before unlock fall through to system sounds and the
generated tone. Delivery and dismissal operations that require Room are marked
in device-protected storage and reconciled after `BOOT_COMPLETED`.

An interrupted active session is also resumed. A session already marked
dismissed advances to the next persisted overlapping alarm instead.

## Always-on backup escalation

Backup escalation is part of every real main alarm rather than an enable/disable
option. The user may configure a 5, 10, or 15 minute inactivity timeout and one
to three vibration reinforcement attempts.

The timeout is measured from the most recent verified puzzle progress. When it
expires, playback switches away from the custom URI to the system fallback
chain, volume is held at maximum, and a finite alarm vibration pattern runs.
Additional vibration attempts occur one minute apart. New verified progress
leaves escalation and restarts the inactivity timer. Preview alarms and
wake-up checks do not escalate.

## Overlapping alarms

The first active alarm retains its puzzle sequence and verified progress.
Distinct main alarms or wake-up checks that fire meanwhile are stored in an
ordered, device-protected queue. Re-delivery of the same logical alarm session
is ignored rather than duplicated. Main-alarm identity includes its scheduled
trigger, so later occurrences of the same recurring alarm remain distinct.

After successful dismissal, the activity closes and the service starts the next
payload. The queue head is retained until the next service session initializes,
so a failed foreground-service request cannot reorder or lose pending alarms.
Each queued main alarm records its normal one-time-disable or recurring
reschedule follow-up when its original delivery arrives.

## Persistence and backup

The direct-boot mirror, active alarm session, and pending queue are operational
state. They are excluded from cloud backup and device transfer. Room schema
version 9 adds the backup timeout and reinforcement-count settings.
