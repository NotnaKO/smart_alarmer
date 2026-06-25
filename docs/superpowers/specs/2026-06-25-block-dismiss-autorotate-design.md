# Design Spec: Block Autorotate on AlarmDismissActivity

## Problem
Shaking the device to solve the "Shake to Dismiss" puzzle or general movement during alarm dismissal can trigger auto-rotation. Auto-rotation destroys and recreates the `AlarmDismissActivity` instance. This recreation resets the puzzle state, shuffling the list of puzzles and resetting the shake count (or other puzzle inputs), causing a highly frustrating user experience.

## Proposed Solution
Lock the orientation of `AlarmDismissActivity` to portrait mode by declaring it in `AndroidManifest.xml`. This prevents rotation lifecycle restarts, keeping the activity, its puzzles, and all current progress intact.

## Proposed Changes

### Android Manifest

#### [MODIFY] [AndroidManifest.xml](file:///home/notnako/smart_alarmer/app/src/main/AndroidManifest.xml)
- Add `android:screenOrientation="portrait"` to the `AlarmDismissActivity` declaration.

```xml
        <!-- AlarmDismissActivity -->
        <activity
            android:name=".ui.dismiss.AlarmDismissActivity"
            android:screenOrientation="portrait"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:launchMode="singleInstance"
            android:excludeFromRecents="true"
            android:exported="false" />
```

## Verification Plan
1. Compile and build the application successfully using `./gradlew build`.
2. Run local unit tests using `./gradlew test` to ensure no regressions.
