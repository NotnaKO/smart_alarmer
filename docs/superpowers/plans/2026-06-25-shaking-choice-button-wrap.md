# Shaking Choice Button Wrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modify the puzzle selection UI layout in the alarm edit bottom sheet so that Russian and other long-named puzzle type filter chips wrap cleanly onto multiple lines instead of squeezing or overflowing.

**Architecture:** Replace the standard horizontal `Row` containing the puzzle selection `FilterChip` items with a `FlowRow` layout, using `verticalArrangement = Arrangement.spacedBy(8.dp)` and `horizontalArrangement = Arrangement.spacedBy(8.dp)`.

**Tech Stack:** Jetpack Compose Layouts (`FlowRow`).

---

### Task 1: Replace Row with FlowRow in MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt:606-643`

- [ ] **Step 1: Replace Row with FlowRow and add vertical arrangement**

Modify the layout in `MainActivity.kt`'s `AlarmEditSheet` around lines 606-643:

```kotlin
                  FlowRow(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                      val puzzleTypes = listOf("MATH", "MEMORY", "TYPING", "SHAKE")
                      puzzleTypes.forEach { type ->
                          val isSelected = selectedPuzzles.contains(type)
                          val displayName = when (type) {
                              "MATH" -> stringResource(com.example.smartalarmer.R.string.puzzle_math)
                              "MEMORY" -> stringResource(com.example.smartalarmer.R.string.puzzle_memory)
                              "TYPING" -> stringResource(com.example.smartalarmer.R.string.puzzle_typing)
                              "SHAKE" -> stringResource(com.example.smartalarmer.R.string.puzzle_shake)
                              else -> type
                          }
                          FilterChip(
                              selected = isSelected,
                              onClick = {
                                  if (isSelected) {
                                      if (selectedPuzzles.size > 1) {
                                          selectedPuzzles.remove(type)
                                          if (puzzleCount > selectedPuzzles.size) {
                                              puzzleCount = selectedPuzzles.size
                                          }
                                      }
                                  } else {
                                      selectedPuzzles.add(type)
                                  }
                              },
                              label = { Text(displayName) },
                              colors = FilterChipDefaults.filterChipColors(
                                  selectedContainerColor = IndigoPrimary,
                                  selectedLabelColor = Color.White,
                                  containerColor = KeyButtonBg,
                                  labelColor = Color.Gray
                              )
                          )
                      }
                  }
```

- [ ] **Step 2: Build project**
Run command: `./gradlew assembleDebug`
Expected: Successful compile and APK build.

- [ ] **Step 3: Run unit tests**
Run: `./gradlew test`
Expected: Unit tests pass.

- [ ] **Step 4: Commit changes**
Run:
```bash
git add app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt
git commit -m "style: wrap puzzle selection filter chips with FlowRow to support long translations"
```
