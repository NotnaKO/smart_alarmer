# Design Spec: Wrap Puzzle Selection Filter Chips in MainActivity

## Problem
In the alarm edit sheet (`MainActivity.kt`), the puzzle types (Math, Memory, Typing, Shake) are presented inside Compose `FilterChip` components inside a horizontal `Row`.
When using the Russian localization, the translations for these types are relatively long (e.g. "Математика", "Память", "Ввод текста", "Встряхивание"). The horizontal `Row` forces all items onto a single line. This results in the chips either being squeezed horizontally (causing awkward vertical text wraps/line breaks inside the chips) or overflowing the screen boundary.

## Proposed Solution
Replace the `Row` holding the puzzle selection filter chips with a Compose `FlowRow`. `FlowRow` automatically flows the items to the next line when they run out of horizontal space, preventing squeezing or clipping while maintaining a clean look in all localizations.

## Proposed Changes

### MainActivity

#### [MODIFY] [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt)
- Replace `Row` with `FlowRow` inside the `AlarmEditSheet` puzzle selection section.
- Add `verticalArrangement = Arrangement.spacedBy(8.dp)` to the `FlowRow` parameter to ensure spacing between wrapped lines.
- The `FlowRow` uses standard Compose APIs available through `androidx.compose.foundation.layout.*`.

```kotlin
// In AlarmEditSheet puzzle selection Column:
FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    val puzzleTypes = listOf("MATH", "MEMORY", "TYPING", "SHAKE")
    // ... FilterChips ...
}
```

## Verification Plan
1. Successfully build the project using `./gradlew build`.
2. Run unit tests using `./gradlew test` to ensure there are no regressions.
