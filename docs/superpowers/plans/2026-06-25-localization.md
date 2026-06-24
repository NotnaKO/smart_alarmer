# Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the Smart Alarmer app UI and notifications into Spanish, German, and Russian, adapting the custom virtual keyboard layout and implementing resilient typing matches.

**Architecture:** Strings are moved from Jetpack Compose and service code to Android resources (`strings.xml`). The custom virtual keyboard layout is updated dynamically using system locale settings, and matches are validated using a custom normalization helper in `TypingEngine.kt` to ignore accents, capitalization, and punctuation.

**Tech Stack:** Kotlin, Jetpack Compose, Android Resource XMLs, JUnit

---

### Task 1: Create and Update String Resource Files

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-es/strings.xml`
- Create: `app/src/main/res/values-de/strings.xml`
- Create: `app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Update default strings.xml**
  Replace the contents of `app/src/main/res/values/strings.xml` with:
  ```xml
  <resources>
      <!-- Application Name -->
      <string name="app_name">Smart Alarmer</string>

      <!-- Main Screen Settings / Errors -->
      <string name="bg_execution_settings">Background Execution Settings</string>
      <string name="bg_execution_desc">To ensure alarms trigger reliably in deep sleep and display over the lockscreen, please verify background settings:</string>
      <string name="disable_battery_limits">Disable Battery Limits</string>
      <string name="xiaomi_settings">Xiaomi Settings</string>
      <string name="dismiss">Dismiss</string>
      <string name="permissions_required">Permissions Required</string>
      <string name="permissions_desc">Please enable all permissions below to ensure alarms wake up your device and display properly over the lock screen.</string>
      <string name="allow_notifications">Allow Notifications</string>
      <string name="allow_alarms">Allow Alarms</string>
      <string name="allow_lockscreen">Allow Lockscreen Display</string>
      <string name="no_alarms_scheduled">No alarms scheduled.\nTap + to add an alarm.</string>
      <string name="test_btn">Test</string>
      <string name="delete_alarm_desc">Delete Alarm</string>
      <string name="add_alarm_desc">Add Alarm</string>

      <!-- Alarm Edit Sheet -->
      <string name="new_alarm">New Alarm</string>
      <string name="edit_alarm">Edit Alarm</string>
      <string name="time_label">Time</string>
      <string name="repeat_days_label">Repeat Days</string>
      <string name="dismiss_puzzles_label">Dismiss Puzzles</string>
      <string name="puzzles_required">Puzzles Required</string>
      <string name="gradual_volume">Gradual Volume</string>
      <string name="gradual_volume_desc">Volume ramps up over 60 seconds</string>
      <string name="cancel">Cancel</string>
      <string name="save">Save</string>

      <!-- Day Summary Labels -->
      <string name="one_time">One-time</string>
      <string name="every_day">Every day</string>
      <string name="weekdays">Weekdays</string>
      <string name="weekends">Weekends</string>

      <!-- Day Name Abbreviations (Single Letter) -->
      <string name="day_m">M</string>
      <string name="day_t">T</string>
      <string name="day_w">W</string>
      <string name="day_th">T</string>
      <string name="day_f">F</string>
      <string name="day_sa">S</string>
      <string name="day_su">S</string>

      <!-- Day Names Short (3 Letters) -->
      <string name="day_mon">Mon</string>
      <string name="day_tue">Tue</string>
      <string name="day_wed">Wed</string>
      <string name="day_thu">Thu</string>
      <string name="day_fri">Fri</string>
      <string name="day_sat">Sat</string>
      <string name="day_sun">Sun</string>

      <!-- Puzzle Names -->
      <string name="puzzle_math">Math</string>
      <string name="puzzle_memory">Memory</string>
      <string name="puzzle_typing">Typing</string>
      <string name="puzzle_shake">Shake</string>

      <!-- Pluralized Toast Resources -->
      <plurals name="hours_plural">
          <item quantity="one">%1$d hour</item>
          <item quantity="other">%1$d hours</item>
      </plurals>
      <plurals name="minutes_plural">
          <item quantity="one">%1$d minute</item>
          <item quantity="other">%1$d minutes</item>
      </plurals>
      <string name="alarm_set_toast">Alarm set for %1$s from now</string>
      <string name="hours_and_minutes_connector">%1$s and %2$s</string>

      <!-- Alarm Dismiss Screen -->
      <string name="task_progress_format">Task %1$d of %2$d</string>
      <string name="your_answer_format">Your Answer: %1$s</string>
      <string name="type_sentence_label">Type this exact sentence:</string>
      <string name="submit_btn">Submit</string>
      <string name="memorize_pattern">Memorize Pattern...</string>
      <string name="repeat_pattern">Repeat Pattern!</string>
      <string name="shake_device">Shake the device!</string>
      <string name="shakes_remaining">Shakes remaining: %1$d</string>

      <!-- Notification Service -->
      <string name="active_alarm_channel_name">Active Alarm</string>
      <string name="wake_up_title">WAKE UP NOW!</string>
      <string name="wake_up_desc">Complete tasks to silence the alarm</string>

      <!-- Typing Quotes -->
      <string-array name="typing_quotes">
          <item>The early bird gets the worm.</item>
          <item>Waking up is the first step to success.</item>
          <item>No snooze allowed. Rise and shine!</item>
          <item>Make today count. Get out of bed.</item>
      </string-array>
  </resources>
  ```

- [ ] **Step 2: Create Spanish translations in values-es/strings.xml**
  Write file `app/src/main/res/values-es/strings.xml` with content:
  ```xml
  <resources>
      <!-- Application Name -->
      <string name="app_name">Smart Alarmer</string>

      <!-- Main Screen Settings / Errors -->
      <string name="bg_execution_settings">Ajustes de ejecución en segundo plano</string>
      <string name="bg_execution_desc">Para garantizar que las alarmas se activen de forma fiable en modo de suspensión profunda y se muestren en la pantalla de bloqueo, verifique los ajustes de segundo plano:</string>
      <string name="disable_battery_limits">Desactivar límites de batería</string>
      <string name="xiaomi_settings">Ajustes de Xiaomi</string>
      <string name="dismiss">Descartar</string>
      <string name="permissions_required">Permisos requeridos</string>
      <string name="permissions_desc">Habilite todos los permisos a continuación para asegurarse de que las alarmas activen su dispositivo y se muestren correctamente en la pantalla de bloqueo.</string>
      <string name="allow_notifications">Permitir notificaciones</string>
      <string name="allow_alarms">Permitir alarmas</string>
      <string name="allow_lockscreen">Permitir visualización en pantalla de bloqueo</string>
      <string name="no_alarms_scheduled">No hay alarmas programadas.\nToque + para añadir una alarma.</string>
      <string name="test_btn">Probar</string>
      <string name="delete_alarm_desc">Eliminar alarma</string>
      <string name="add_alarm_desc">Añadir alarma</string>

      <!-- Alarm Edit Sheet -->
      <string name="new_alarm">Nueva alarma</string>
      <string name="edit_alarm">Editar alarma</string>
      <string name="time_label">Hora</string>
      <string name="repeat_days_label">Repetir días</string>
      <string name="dismiss_puzzles_label">Puzzles para apagar</string>
      <string name="puzzles_required">Puzzles requeridos</string>
      <string name="gradual_volume">Volumen gradual</string>
      <string name="gradual_volume_desc">El volumen aumenta durante 60 segundos</string>
      <string name="cancel">Cancelar</string>
      <string name="save">Guardar</string>

      <!-- Day Summary Labels -->
      <string name="one_time">Una vez</string>
      <string name="every_day">Todos los días</string>
      <string name="weekdays">Días laborables</string>
      <string name="weekends">Fines de semana</string>

      <!-- Day Name Abbreviations (Single Letter) -->
      <string name="day_m">L</string>
      <string name="day_t">M</string>
      <string name="day_w">X</string>
      <string name="day_th">J</string>
      <string name="day_f">V</string>
      <string name="day_sa">S</string>
      <string name="day_su">D</string>

      <!-- Day Names Short (3 Letters) -->
      <string name="day_mon">Lun</string>
      <string name="day_tue">Mar</string>
      <string name="day_wed">Mié</string>
      <string name="day_thu">Jue</string>
      <string name="day_fri">Vie</string>
      <string name="day_sat">Sáb</string>
      <string name="day_sun">Dom</string>

      <!-- Puzzle Names -->
      <string name="puzzle_math">Matemáticas</string>
      <string name="puzzle_memory">Memoria</string>
      <string name="puzzle_typing">Escritura</string>
      <string name="puzzle_shake">Agitar</string>

      <!-- Pluralized Toast Resources -->
      <plurals name="hours_plural">
          <item quantity="one">%1$d hora</item>
          <item quantity="other">%1$d horas</item>
      </plurals>
      <plurals name="minutes_plural">
          <item quantity="one">%1$d minuto</item>
          <item quantity="other">%1$d minutos</item>
      </plurals>
      <string name="alarm_set_toast">Alarma programada para dentro de %1$s</string>
      <string name="hours_and_minutes_connector">%1$s y %2$s</string>

      <!-- Alarm Dismiss Screen -->
      <string name="task_progress_format">Tarea %1$d de %2$d</string>
      <string name="your_answer_format">Tu respuesta: %1$s</string>
      <string name="type_sentence_label">Escribe esta frase exacta:</string>
      <string name="submit_btn">Enviar</string>
      <string name="memorize_pattern">Memoriza el patrón...</string>
      <string name="repeat_pattern">¡Repite el patrón!</string>
      <string name="shake_device">¡Agita el dispositivo!</string>
      <string name="shakes_remaining">Sacudidas restantes: %1$d</string>

      <!-- Notification Service -->
      <string name="active_alarm_channel_name">Alarma activa</string>
      <string name="wake_up_title">¡DESPIÉRTATE YA!</string>
      <string name="wake_up_desc">Completa las tareas para apagar la alarma</string>

      <!-- Typing Quotes -->
      <string-array name="typing_quotes">
          <item>Al que madruga, Dios le ayuda.</item>
          <item>El primer paso para triunfar es levantarse.</item>
          <item>No se permite dormir mas. A levantarse!</item>
          <item>Haz que hoy cuente. Sal de la cama.</item>
      </string-array>
  </resources>
  ```

- [ ] **Step 3: Create German translations in values-de/strings.xml**
  Write file `app/src/main/res/values-de/strings.xml` with content:
  ```xml
  <resources>
      <!-- Application Name -->
      <string name="app_name">Smart Alarmer</string>

      <!-- Main Screen Settings / Errors -->
      <string name="bg_execution_settings">Hintergrund-Ausführungseinstellungen</string>
      <string name="bg_execution_desc">Um sicherzustellen, dass Alarme im Tiefschlaf zuverlässig auslösen und über dem Sperrbildschirm angezeigt werden, überprüfen Sie bitte die Hintergrund-Einstellungen:</string>
      <string name="disable_battery_limits">Akku-Limits deaktivieren</string>
      <string name="xiaomi_settings">Xiaomi-Einstellungen</string>
      <string name="dismiss">Verwerfen</string>
      <string name="permissions_required">Erforderliche Berechtigungen</string>
      <string name="permissions_desc">Bitte aktivieren Sie alle unten stehenden Berechtigungen, damit Alarme Ihr Gerät wecken und korrekt auf dem Sperrbildschirm angezeigt werden.</string>
      <string name="allow_notifications">Benachrichtigungen erlauben</string>
      <string name="allow_alarms">Alarme erlauben</string>
      <string name="allow_lockscreen">Sperrbildschirm-Anzeige erlauben</string>
      <string name="no_alarms_scheduled">Keine Alarme geplant.\nTippen Sie auf +, um einen Alarm hinzuzufügen.</string>
      <string name="test_btn">Testen</string>
      <string name="delete_alarm_desc">Alarm löschen</string>
      <string name="add_alarm_desc">Alarm hinzufügen</string>

      <!-- Alarm Edit Sheet -->
      <string name="new_alarm">Neuer Alarm</string>
      <string name="edit_alarm">Alarm bearbeiten</string>
      <string name="time_label">Uhrzeit</string>
      <string name="repeat_days_label">Wiederholungstage</string>
      <string name="dismiss_puzzles_label">Rätsel zum Ausschalten</string>
      <string name="puzzles_required">Erforderliche Rätsel</string>
      <string name="gradual_volume">Ansteigende Lautstärke</string>
      <string name="gradual_volume_desc">Lautstärke steigt über 60 Sekunden an</string>
      <string name="cancel">Abbrechen</string>
      <string name="save">Speichern</string>

      <!-- Day Summary Labels -->
      <string name="one_time">Einmalig</string>
      <string name="every_day">Täglich</string>
      <string name="weekdays">Wochentage</string>
      <string name="weekends">Wochenenden</string>

      <!-- Day Name Abbreviations (Single Letter) -->
      <string name="day_m">M</string>
      <string name="day_t">D</string>
      <string name="day_w">M</string>
      <string name="day_th">D</string>
      <string name="day_f">F</string>
      <string name="day_sa">S</string>
      <string name="day_su">S</string>

      <!-- Day Names Short (3 Letters) -->
      <string name="day_mon">Mo</string>
      <string name="day_tue">Di</string>
      <string name="day_wed">Mi</string>
      <string name="day_thu">Do</string>
      <string name="day_fri">Fr</string>
      <string name="day_sat">Sa</string>
      <string name="day_sun">So</string>

      <!-- Puzzle Names -->
      <string name="puzzle_math">Mathe</string>
      <string name="puzzle_memory">Gedächtnis</string>
      <string name="puzzle_typing">Tippen</string>
      <string name="puzzle_shake">Schütteln</string>

      <!-- Pluralized Toast Resources -->
      <plurals name="hours_plural">
          <item quantity="one">%1$d Stunde</item>
          <item quantity="other">%1$d Stunden</item>
      </plurals>
      <plurals name="minutes_plural">
          <item quantity="one">%1$d Minute</item>
          <item quantity="other">%1$d Minuten</item>
      </plurals>
      <string name="alarm_set_toast">Alarm in %1$s ab jetzt gestellt</string>
      <string name="hours_and_minutes_connector">%1$s und %2$s</string>

      <!-- Alarm Dismiss Screen -->
      <string name="task_progress_format">Aufgabe %1$d von %2$d</string>
      <string name="your_answer_format">Deine Antwort: %1$s</string>
      <string name="type_sentence_label">Tippe diesen Satz exakt ab:</string>
      <string name="submit_btn">Bestätigen</string>
      <string name="memorize_pattern">Muster merken...</string>
      <string name="repeat_pattern">Muster wiederholen!</string>
      <string name="shake_device">Gerät schütteln!</string>
      <string name="shakes_remaining">Verbleibende Erschütterungen: %1$d</string>

      <!-- Notification Service -->
      <string name="active_alarm_channel_name">Aktiver Alarm</string>
      <string name="wake_up_title">JETZT AUFSTEHEN!</string>
      <string name="wake_up_desc">Löse die Aufgaben, um den Alarm auszuschalten</string>

      <!-- Typing Quotes -->
      <string-array name="typing_quotes">
          <item>Morgenstund hat Gold im Mund.</item>
          <item>Aufstehen ist der erste Schritt zum Erfolg.</item>
          <item>Schlummern verboten. Aufstehen und strahlen!</item>
          <item>Mach das Beste aus dem Tag. Raus aus dem Bett.</item>
      </string-array>
  </resources>
  ```

- [ ] **Step 4: Create Russian translations in values-ru/strings.xml**
  Write file `app/src/main/res/values-ru/strings.xml` with content:
  ```xml
  <resources>
      <!-- Application Name -->
      <string name="app_name">Умный будильник</string>

      <!-- Main Screen Settings / Errors -->
      <string name="bg_execution_settings">Настройки фоновой работы</string>
      <string name="bg_execution_desc">Чтобы гарантировать надежное срабатывание будильника в режиме глубокого сна и его отображение поверх экрана блокировки, проверьте фоновые настройки:</string>
      <string name="disable_battery_limits">Отключить лимиты батареи</string>
      <string name="xiaomi_settings">Настройки Xiaomi</string>
      <string name="dismiss">Скрыть</string>
      <string name="permissions_required">Требуются разрешения</string>
      <string name="permissions_desc">Пожалуйста, предоставьте все разрешения ниже, чтобы будильник мог разбудить устройство и корректно отображаться на экране блокировки.</string>
      <string name="allow_notifications">Разрешить уведомления</string>
      <string name="allow_alarms">Разрешить будильники</string>
      <string name="allow_lockscreen">Поверх экрана блокировки</string>
      <string name="no_alarms_scheduled">Нет запланированных будильников.\nНажмите +, чтобы добавить.</string>
      <string name="test_btn">Тест</string>
      <string name="delete_alarm_desc">Удалить будильник</string>
      <string name="add_alarm_desc">Добавить будильник</string>

      <!-- Alarm Edit Sheet -->
      <string name="new_alarm">Новый будильник</string>
      <string name="edit_alarm">Изменить будильник</string>
      <string name="time_label">Время</string>
      <string name="repeat_days_label">Дни повтора</string>
      <string name="dismiss_puzzles_label">Головоломки для выключения</string>
      <string name="puzzles_required">Количество головоломок</string>
      <string name="gradual_volume">Нарастающая громкость</string>
      <string name="gradual_volume_desc">Громкость нарастает в течение 60 секунд</string>
      <string name="cancel">Отмена</string>
      <string name="save">Сохранить</string>

      <!-- Day Summary Labels -->
      <string name="one_time">Однократно</string>
      <string name="every_day">Каждый день</string>
      <string name="weekdays">Будни</string>
      <string name="weekends">Выходные</string>

      <!-- Day Name Abbreviations (Single Letter) -->
      <string name="day_m">П</string>
      <string name="day_t">В</string>
      <string name="day_w">С</string>
      <string name="day_th">Ч</string>
      <string name="day_f">П</string>
      <string name="day_sa">С</string>
      <string name="day_su">В</string>

      <!-- Day Names Short (3 Letters) -->
      <string name="day_mon">Пн</string>
      <string name="day_tue">Вт</string>
      <string name="day_wed">Ср</string>
      <string name="day_thu">Чт</string>
      <string name="day_fri">Пт</string>
      <string name="day_sat">Сб</string>
      <string name="day_sun">Вс</string>

      <!-- Puzzle Names -->
      <string name="puzzle_math">Математика</string>
      <string name="puzzle_memory">Память</string>
      <string name="puzzle_typing">Ввод текста</string>
      <string name="puzzle_shake">Встряхивание</string>

      <!-- Pluralized Toast Resources -->
      <plurals name="hours_plural">
          <item quantity="one">%1$d час</item>
          <item quantity="few">%1$d часа</item>
          <item quantity="many">%1$d часов</item>
          <item quantity="other">%1$d часов</item>
      </plurals>
      <plurals name="minutes_plural">
          <item quantity="one">%1$d минута</item>
          <item quantity="few">%1$d минуты</item>
          <item quantity="many">%1$d минут</item>
          <item quantity="other">%1$d минут</item>
      </plurals>
      <string name="alarm_set_toast">Будильник заведен: прозвенит через %1$s</string>
      <string name="hours_and_minutes_connector">%1$s и %2$s</string>

      <!-- Alarm Dismiss Screen -->
      <string name="task_progress_format">Задача %1$d из %2$d</string>
      <string name="your_answer_format">Ваш ответ: %1$s</string>
      <string name="type_sentence_label">Введите это предложение точно:</string>
      <string name="submit_btn">Отправить</string>
      <string name="memorize_pattern">Запомните узор...</string>
      <string name="repeat_pattern">Повторите узор!</string>
      <string name="shake_device">Встряхните устройство!</string>
      <string name="shakes_remaining">Осталось встряхиваний: %1$d</string>

      <!-- Notification Service -->
      <string name="active_alarm_channel_name">Активный будильник</string>
      <string name="wake_up_title">ПРОСЫПАЙСЯ!</string>
      <string name="wake_up_desc">Выполните задания, чтобы отключить будильник</string>

      <!-- Typing Quotes -->
      <string-array name="typing_quotes">
          <item>Кто рано встает, тому Бог подает.</item>
          <item>Ранний подъем — первый шаг к успеху.</item>
          <item>Дремать нельзя. Просыпайся и сияй!</item>
          <item>Сделай этот день важным. Вставай с постели.</item>
      </string-array>
  </resources>
  ```

- [ ] **Step 5: Run tests and commit resource files**
  Command: `./gradlew testDebugUnitTest`
  Expected: PASS
  Commit: `git add app/src/main/res/ && git commit -m "resources: add Spanish, German, and Russian localized string values"`

---

### Task 2: Implement Accent-Insensitive and Robust Matching in TypingEngine

**Files:**
- Create: `app/src/test/java/com/example/smartalarmer/puzzle/TypingEngineTest.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/puzzle/TypingEngine.kt`

- [ ] **Step 1: Write a unit test for localized normalizations and matches**
  Create `app/src/test/java/com/example/smartalarmer/puzzle/TypingEngineTest.kt` with:
  ```kotlin
  package com.example.smartalarmer.puzzle

  import org.junit.Assert.assertTrue
  import org.junit.Assert.assertFalse
  import org.junit.Test

  class TypingEngineTest {
      @Test
      fun testNormalizationAndMatching() {
          // English matches
          assertTrue(TypingEngine.isMatch("The early bird gets the worm.", "the early bird gets the worm"))
          assertTrue(TypingEngine.isMatch("No snooze allowed. Rise and shine!", "no snooze allowed rise and shine"))

          // Spanish matches (accent-insensitive)
          assertTrue(TypingEngine.isMatch("Al que madruga, Dios le ayuda.", "al que madruga dios le ayuda"))
          assertTrue(TypingEngine.isMatch("No se permite dormir más.", "no se permite dormir mas"))

          // German matches
          assertTrue(TypingEngine.isMatch("Morgenstund hat Gold im Mund.", "morgenstund hat gold im mund"))

          // Russian matches (punctuation-tolerant)
          assertTrue(TypingEngine.isMatch("Кто рано встает, тому Бог подает.", "кто рано встает тому бог подает"))
          assertTrue(TypingEngine.isMatch("Ранний подъем — первый шаг к успеху.", "ранний подъем первый шаг к успеху"))

          // Non-matches
          assertFalse(TypingEngine.isMatch("The early bird", "The late bird"))
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails**
  Command: `./gradlew :app:testDebugUnitTest --tests "com.example.smartalarmer.puzzle.TypingEngineTest"`
  Expected: FAIL (either compilation error or failing assertion due to exact matching)

- [ ] **Step 3: Update TypingEngine with normalization code**
  Modify `app/src/main/java/com/example/smartalarmer/puzzle/TypingEngine.kt` to:
  ```kotlin
  package com.example.smartalarmer.puzzle

  object TypingEngine : TypingPuzzleProvider {
      override fun getRandomQuote(quotes: List<String>): String = quotes.random()

      override fun isMatch(target: String, input: String): Boolean {
          return normalize(target) == normalize(input)
      }

      private fun normalize(str: String): String {
          // 1. Convert to lowercase
          var normalized = str.trim().lowercase()
          
          // 2. Remove accents/diacritics (e.g. á -> a, ü -> u)
          normalized = java.text.Normalizer.normalize(normalized, java.text.Normalizer.Form.NFD)
          normalized = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
          
          // 3. Keep only letters and spaces (stripping punctuation like commas, periods, dashes, quotes, etc.)
          normalized = normalized.replace("[^\\p{L}\\s]".toRegex(), "")
          
          // 4. Collapse multiple spaces into one
          return normalized.replace("\\s+".toRegex(), " ").trim()
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Command: `./gradlew :app:testDebugUnitTest --tests "com.example.smartalarmer.puzzle.TypingEngineTest"`
  Expected: PASS

- [ ] **Step 5: Commit**
  Commit: `git add app/src/test/java/com/example/smartalarmer/puzzle/TypingEngineTest.kt app/src/main/java/com/example/smartalarmer/puzzle/TypingEngine.kt && git commit -m "feat: implement robust and accent-insensitive matching in TypingEngine with unit tests"`

---

### Task 3: Implement Dynamic Keyboard Layout Resolution

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/ui/dismiss/KeyboardLayouts.kt`
- Create: `app/src/test/java/com/example/smartalarmer/ui/dismiss/KeyboardLayoutsTest.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt`

- [ ] **Step 1: Write keyboard layouts unit test**
  Create `app/src/test/java/com/example/smartalarmer/ui/dismiss/KeyboardLayoutsTest.kt` with:
  ```kotlin
  package com.example.smartalarmer.ui.dismiss

  import org.junit.Assert.assertEquals
  import org.junit.Test

  class KeyboardLayoutsTest {
      @Test
      fun testKeyboardLayoutResolution() {
          val ruLayout = KeyboardLayouts.getLayoutForLanguage("ru")
          assertEquals('й', ruLayout[0][0])
          assertEquals('э', ruLayout[1].last())

          val deLayout = KeyboardLayouts.getLayoutForLanguage("de")
          assertEquals('z', deLayout[0][5])
          assertEquals('ü', deLayout[0].last())

          val esLayout = KeyboardLayouts.getLayoutForLanguage("es")
          assertEquals('ñ', esLayout[1].last())

          val enLayout = KeyboardLayouts.getLayoutForLanguage("en")
          assertEquals('y', enLayout[0][5])
          assertEquals('l', enLayout[1].last())
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails**
  Command: `./gradlew :app:testDebugUnitTest --tests "com.example.smartalarmer.ui.dismiss.KeyboardLayoutsTest"`
  Expected: FAIL (compilation error)

- [ ] **Step 3: Create KeyboardLayouts helper**
  Write file `app/src/main/java/com/example/smartalarmer/ui/dismiss/KeyboardLayouts.kt` with:
  ```kotlin
  package com.example.smartalarmer.ui.dismiss

  object KeyboardLayouts {
      fun getLayoutForLanguage(language: String): List<List<Char>> {
          return when (language) {
              "ru" -> listOf(
                  listOf('й', 'ц', 'у', 'к', 'е', 'н', 'г', 'ш', 'щ', 'з', 'х', 'ъ'),
                  listOf('ф', 'ы', 'в', 'а', 'п', 'р', 'о', 'л', 'д', 'ж', 'э'),
                  listOf('я', 'ч', 'с', 'м', 'и', 'т', 'ь', 'б', 'ю', '.', '!')
              )
              "de" -> listOf(
                  listOf('q', 'w', 'e', 'r', 't', 'z', 'u', 'i', 'o', 'p', 'ü'),
                  listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ö', 'ä'),
                  listOf('y', 'x', 'c', 'v', 'b', 'n', 'm', 'ß', '.', '!')
              )
              "es" -> listOf(
                  listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
                  listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ñ'),
                  listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', '.', '!')
              )
              else -> listOf(
                  listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
                  listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
                  listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', '.', '!')
              )
          }
      }
  }
  ```

- [ ] **Step 4: Update VirtualKeyboard and integration in AlarmDismissScreen.kt**
  Modify `VirtualKeyboard` in `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt` to load dynamic layouts based on language.
  
  Replace lines 213-291 with:
  ```kotlin
  @Composable
  fun VirtualKeyboard(
      onKeyClick: (Char) -> Unit,
      onBackspace: () -> Unit,
      modifier: Modifier = Modifier
  ) {
      var isShifted by remember { mutableStateOf(false) }
      val context = androidx.compose.ui.platform.LocalContext.current
      val locale = context.resources.configuration.locales[0] ?: java.util.Locale.getDefault()
      val language = locale.language

      val rows = remember(isShifted, language) {
          KeyboardLayouts.getLayoutForLanguage(language).map { row ->
              if (isShifted) {
                  row.map { it.uppercaseChar() }
              } else {
                  row
              }
          }
      }

      Column(
          modifier = modifier
              .fillMaxWidth()
              .background(KeyboardBg, RoundedCornerShape(16.dp))
              .padding(8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
          // Rows 1 & 2
          rows.take(2).forEach { row ->
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
              ) {
                  row.forEach { char ->
                      KeyButton(text = char.toString(), onClick = { onKeyClick(char) }, modifier = Modifier.weight(1f))
                  }
              }
          }

          // Row 3 (Shift, letters & punctuation, Backspace)
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
              verticalAlignment = Alignment.CenterVertically
          ) {
              KeyButton(
                  text = "⇧",
                  onClick = { isShifted = !isShifted },
                  containerColor = if (isShifted) IndigoPrimary else KeyButtonBgActive,
                  modifier = Modifier.weight(1.5f)
              )

              rows[2].forEach { char ->
                  KeyButton(text = char.toString(), onClick = { onKeyClick(char) }, modifier = Modifier.weight(1f))
              }

              KeyButton(
                  text = "⌫",
                  onClick = onBackspace,
                  containerColor = KeyButtonBgActive,
                  modifier = Modifier.weight(1.5f)
              )
          }

          // Row 4 (Space)
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
          ) {
              KeyButton(
                  text = "Space",
                  onClick = { onKeyClick(' ') },
                  modifier = Modifier.fillMaxWidth()
              )
          }
      }
  }
  ```

- [ ] **Step 5: Run tests to verify all pass**
  Command: `./gradlew testDebugUnitTest`
  Expected: PASS

- [ ] **Step 6: Commit**
  Commit: `git add app/src/main/java/com/example/smartalarmer/ui/dismiss/KeyboardLayouts.kt app/src/test/java/com/example/smartalarmer/ui/dismiss/KeyboardLayoutsTest.kt app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt && git commit -m "feat: resolve dynamic keyboard layouts based on active system locale"`

---

### Task 4: Integrate String Resource Loading in UI and Service Code

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/service/AlarmService.kt`

- [ ] **Step 1: Localize MainViewModel saveAlarm toast**
  Replace lines 81-87 in `app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt` with:
  ```kotlin
              // Show Toast with remaining time
              val nextTrigger = AlarmScheduler.calculateNextTriggerTime(scheduledAlarm, java.util.Calendar.getInstance())
              val diffMs = nextTrigger.timeInMillis - System.currentTimeMillis()
              val hours = diffMs / (3600 * 1000)
              val minutes = (diffMs % (3600 * 1000)) / (60 * 1000)
              
              val hoursText = if (hours > 0) {
                  context.resources.getQuantityString(com.example.smartalarmer.R.plurals.hours_plural, hours.toInt(), hours.toInt())
              } else ""
              
              val minutesText = context.resources.getQuantityString(com.example.smartalarmer.R.plurals.minutes_plural, minutes.toInt(), minutes.toInt())
              
              val timeText = if (hours > 0) {
                  context.getString(com.example.smartalarmer.R.string.hours_and_minutes_connector, hoursText, minutesText)
              } else {
                  minutesText
              }
              
              val toastMsg = context.getString(com.example.smartalarmer.R.string.alarm_set_toast, timeText)
              android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_LONG).show()
  ```

- [ ] **Step 2: Localize AlarmDismissScreen UI texts**
  Modify the `Text` labels in `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt`:
  
  At line 65, replace `text = "Task ${currentTaskIndex + 1} of ${puzzles.size}"` with:
  ```kotlin
  text = androidx.compose.ui.res.stringResource(
      R.string.task_progress_format,
      currentTaskIndex + 1,
      puzzles.size
  )
  ```
  
  At line 112, replace `text = "Your Answer: $input"` with:
  ```kotlin
  text = androidx.compose.ui.res.stringResource(R.string.your_answer_format, input)
  ```
  
  At line 174, replace `text = "Type this exact sentence:"` with:
  ```kotlin
  text = androidx.compose.ui.res.stringResource(R.string.type_sentence_label)
  ```
  
  At line 207, replace `Text("Submit")` with:
  ```kotlin
  Text(androidx.compose.ui.res.stringResource(R.string.submit_btn))
  ```

  At line 346, replace `text = if (isShowingSequence) "Memorize Pattern..." else "Repeat Pattern!"` with:
  ```kotlin
  text = if (isShowingSequence) {
      androidx.compose.ui.res.stringResource(R.string.memorize_pattern)
  } else {
      androidx.compose.ui.res.stringResource(R.string.repeat_pattern)
  }
  ```

  At line 431, replace `text = "Shake the device!"` with:
  ```kotlin
  text = androidx.compose.ui.res.stringResource(R.string.shake_device)
  ```

  At line 438, replace `text = "Shakes remaining: $shakeCount"` with:
  ```kotlin
  text = androidx.compose.ui.res.stringResource(R.string.shakes_remaining, shakeCount)
  ```

- [ ] **Step 3: Localize AlarmService notifications**
  In `app/src/main/java/com/example/smartalarmer/service/AlarmService.kt`, update the notification generation:
  Replace lines 36-39 and 58-65 with:
  ```kotlin
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
              val channel = NotificationChannel(
                  channelId,
                  getString(com.example.smartalarmer.R.string.active_alarm_channel_name),
                  NotificationManager.IMPORTANCE_HIGH
              )
              notificationManager.createNotificationChannel(channel)
          }
  ```
  and:
  ```kotlin
          val notification = NotificationCompat.Builder(this, channelId)
              .setContentTitle(getString(com.example.smartalarmer.R.string.wake_up_title))
              .setContentText(getString(com.example.smartalarmer.R.string.wake_up_desc))
              .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
              .setPriority(NotificationCompat.PRIORITY_MAX)
              .setCategory(NotificationCompat.CATEGORY_ALARM)
              .setFullScreenIntent(fullScreenPendingIntent, true)
              .build()
  ```

- [ ] **Step 4: Localize MainActivity UI texts**
  We must replace all remaining hardcoded UI texts in `app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt`.
  
  **Warning & Permission Cards:**
  - Line 150: `text = "Background Execution Settings"` -> `text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.bg_execution_settings)`
  - Line 157: `text = "To ensure alarms trigger..."` -> `text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.bg_execution_desc)`
  - Line 185: `Text("Disable Battery Limits", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.disable_battery_limits), ...)`
  - Line 206: `Text("Xiaomi Settings", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.xiaomi_settings), ...)`
  - Line 218: `Text("Dismiss", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.dismiss), ...)`
  - Line 237: `text = "Permissions Required"` -> `text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.permissions_required)`
  - Line 244: `text = "Please enable all permissions..."` -> `text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.permissions_desc)`
  - Line 264: `Text("Allow Notifications", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.allow_notifications), ...)`
  - Line 281: `Text("Allow Alarms", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.allow_alarms), ...)`
  - Line 298: `Text("Allow Lockscreen Display", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.allow_lockscreen), ...)`

  **Titles & Empty States:**
  - Line 307: `text = "Smart Alarmer"` -> `text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.app_name)`
  - Line 322: `text = "No alarms scheduled...\n..."` -> `text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.no_alarms_scheduled)`
  
  **Alarm Card (AlarmItemCard):**
  - Line 386-394, replace `daysSummary` generation logic with:
    ```kotlin
    val daysSummary = when {
        daysList.isEmpty() -> context.getString(com.example.smartalarmer.R.string.one_time)
        daysList.size == 7 -> context.getString(com.example.smartalarmer.R.string.every_day)
        daysList.containsAll(listOf(1, 2, 3, 4, 5)) && daysList.size == 5 -> context.getString(com.example.smartalarmer.R.string.weekdays)
        daysList.containsAll(listOf(6, 7)) && daysList.size == 2 -> context.getString(com.example.smartalarmer.R.string.weekends)
        else -> {
            val names = listOf(
                context.getString(com.example.smartalarmer.R.string.day_mon),
                context.getString(com.example.smartalarmer.R.string.day_tue),
                context.getString(com.example.smartalarmer.R.string.day_wed),
                context.getString(com.example.smartalarmer.R.string.day_thu),
                context.getString(com.example.smartalarmer.R.string.day_fri),
                context.getString(com.example.smartalarmer.R.string.day_sat),
                context.getString(com.example.smartalarmer.R.string.day_sun)
            )
            daysList.sorted().joinToString(", ") { names[it - 1] }
        }
    }
    ```
  - Line 397-398, replace `puzzlesText` generation logic with:
    ```kotlin
    val puzzlesText = alarm.puzzlesList.split(",")
        .joinToString(", ") { puzzleId ->
            val resId = when (puzzleId.trim().uppercase()) {
                "MATH" -> com.example.smartalarmer.R.string.puzzle_math
                "MEMORY" -> com.example.smartalarmer.R.string.puzzle_memory
                "TYPING" -> com.example.smartalarmer.R.string.puzzle_typing
                "SHAKE" -> com.example.smartalarmer.R.string.puzzle_shake
                else -> com.example.smartalarmer.R.string.puzzle_math
            }
            context.getString(resId)
        }
    ```
  - Line 399, replace `gradualText` with:
    ```kotlin
    val gradualText = if (alarm.isGradualVolume) " • " + context.getString(com.example.smartalarmer.R.string.gradual_volume) else ""
    ```
  - Line 445: `Text("Test", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.test_btn), ...)`
  - Line 122: `Icon(..., contentDescription = "Add Alarm")` -> `Icon(..., contentDescription = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.add_alarm_desc))`
  - Line 463: `Icon(..., contentDescription = "Delete Alarm")` -> `Icon(..., contentDescription = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.delete_alarm_desc))`

  **Alarm Edit Sheet (AlarmEditSheet):**
  - Line 507: `text = if (alarm == null) "New Alarm" else "Edit Alarm"` -> `text = if (alarm == null) androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.new_alarm) else androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.edit_alarm)`
  - Line 534: `Text("Time", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.time_label), ...)`
  - Line 545: `Text("Repeat Days", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.repeat_days_label), ...)`
  - Line 551: replace `dayLabels` with:
    ```kotlin
    val dayLabels = listOf(
        context.getString(com.example.smartalarmer.R.string.day_m),
        context.getString(com.example.smartalarmer.R.string.day_t),
        context.getString(com.example.smartalarmer.R.string.day_w),
        context.getString(com.example.smartalarmer.R.string.day_th),
        context.getString(com.example.smartalarmer.R.string.day_f),
        context.getString(com.example.smartalarmer.R.string.day_sa),
        context.getString(com.example.smartalarmer.R.string.day_su)
    )
    ```
  - Line 578: `Text("Dismiss Puzzles", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.dismiss_puzzles_label), ...)`
  - Line 601: `label = { Text(type) }` -> replace with:
    ```kotlin
    label = {
        val displayName = when (type) {
            "MATH" -> context.getString(com.example.smartalarmer.R.string.puzzle_math)
            "MEMORY" -> context.getString(com.example.smartalarmer.R.string.puzzle_memory)
            "TYPING" -> context.getString(com.example.smartalarmer.R.string.puzzle_typing)
            "SHAKE" -> context.getString(com.example.smartalarmer.R.string.puzzle_shake)
            else -> type
        }
        Text(displayName)
    }
    ```
  - Line 619: `Text("Puzzles Required", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.puzzles_required), ...)`
  - Line 653: `Text("Gradual Volume", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.gradual_volume), ...)`
  - Line 654: `Text("Volume ramps up over 60 seconds", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.gradual_volume_desc), ...)`
  - Line 681: `Text("Cancel")` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.cancel))`
  - Line 692: `Text("Save", ...)` -> `Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.save), ...)`

- [ ] **Step 5: Run full test suite and verify compilation**
  Command: `./gradlew testDebugUnitTest`
  Expected: PASS

- [ ] **Step 6: Commit**
  Commit: `git add app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt app/src/main/java/com/example/smartalarmer/service/AlarmService.kt && git commit -m "feat: localize UI texts and foreground notifications"`
