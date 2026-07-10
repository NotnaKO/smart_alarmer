package com.example.smartalarmer.domain

import com.example.smartalarmer.data.Alarm

enum class AlarmDay(val isoValue: Int) {
    MONDAY(1),
    TUESDAY(2),
    WEDNESDAY(3),
    THURSDAY(4),
    FRIDAY(5),
    SATURDAY(6),
    SUNDAY(7);

    companion object {
        fun fromIsoValue(value: Int): AlarmDay? = entries.firstOrNull { it.isoValue == value }
    }
}

class AlarmDays private constructor(val values: Set<AlarmDay>) {
    val encoded: String = values
        .sortedBy(AlarmDay::isoValue)
        .joinToString(",") { it.isoValue.toString() }

    val isOneTime: Boolean
        get() = values.isEmpty()

    override fun equals(other: Any?): Boolean = other is AlarmDays && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "AlarmDays($encoded)"

    companion object {
        val ONE_TIME = AlarmDays(emptySet())

        fun of(values: Iterable<AlarmDay>): AlarmDays = AlarmDays(values.toSet())

        fun parse(encoded: String?): AlarmDays {
            if (encoded.isNullOrBlank()) return ONE_TIME
            return of(
                encoded.split(",")
                    .mapNotNull { token -> token.trim().toIntOrNull() }
                    .mapNotNull(AlarmDay::fromIsoValue)
            )
        }
    }
}

enum class PuzzleType {
    MATH,
    TYPING,
    MEMORY,
    SHAKE
}

class PuzzleSelection private constructor(val values: Set<PuzzleType>) {
    val encoded: String = values
        .sortedBy(PuzzleType::ordinal)
        .joinToString(",") { it.name }

    override fun equals(other: Any?): Boolean = other is PuzzleSelection && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "PuzzleSelection($encoded)"

    companion object {
        val DEFAULT = PuzzleSelection(setOf(PuzzleType.MATH))

        fun of(values: Iterable<PuzzleType>): PuzzleSelection {
            val selected = values.toSet()
            return if (selected.isEmpty()) DEFAULT else PuzzleSelection(selected)
        }

        fun parse(encoded: String?): PuzzleSelection {
            if (encoded.isNullOrBlank()) return DEFAULT
            return of(
                encoded.split(",").mapNotNull { token ->
                    runCatching { PuzzleType.valueOf(token.trim().uppercase()) }.getOrNull()
                }
            )
        }
    }
}

val Alarm.repeatDays: AlarmDays
    get() = AlarmDays.parse(daysOfWeek)

val Alarm.puzzleSelection: PuzzleSelection
    get() = PuzzleSelection.parse(puzzlesList)
