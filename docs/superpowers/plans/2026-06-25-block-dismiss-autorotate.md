# Block Dismiss Auto-rotation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock the `AlarmDismissActivity` screen orientation to portrait to prevent task restarts during shaking/alarm dismissal.

**Architecture:** Add `android:screenOrientation="portrait"` to `AlarmDismissActivity` in `AndroidManifest.xml`.

**Tech Stack:** Android manifest (XML).

---

### Task 1: Update AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:51-59`

- [ ] **Step 1: Modify AndroidManifest.xml to add screenOrientation attribute**

Modify `app/src/main/AndroidManifest.xml` to include `android:screenOrientation="portrait"` on the `.ui.dismiss.AlarmDismissActivity` `<activity>` tag.

Exact replacement target in `app/src/main/AndroidManifest.xml`:
```xml
        <!-- AlarmDismissActivity -->
        <activity
            android:name=".ui.dismiss.AlarmDismissActivity"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:launchMode="singleInstance"
            android:excludeFromRecents="true"
            android:exported="false" />
```

Replacement content:
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

- [ ] **Step 2: Compile the app to verify manifest syntax correctness**

Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the local unit tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (all tests passing)

- [ ] **Step 4: Commit changes**

Run:
```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: lock AlarmDismissActivity orientation to portrait"
```
