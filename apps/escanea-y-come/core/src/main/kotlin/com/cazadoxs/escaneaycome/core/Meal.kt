package com.cazadoxs.escaneaycome.core

/** The Spanish day has five eating moments, merienda included. */
enum class Meal(val label: String) {
    DESAYUNO("Desayuno"),
    COMIDA("Comida"),
    MERIENDA("Merienda"),
    CENA("Cena"),
    PICOTEO("Picoteo");

    companion object {
        /** Sensible default for the current time, Spanish timetable (lunch at 14h, dinner at 21h). */
        fun forTime(hour: Int, minute: Int = 0): Meal {
            val m = hour * 60 + minute
            return when (m) {
                in 5 * 60 until 11 * 60 + 30 -> DESAYUNO
                in 11 * 60 + 30 until 16 * 60 + 30 -> COMIDA
                in 16 * 60 + 30 until 19 * 60 + 30 -> MERIENDA
                in 19 * 60 + 30 until 23 * 60 + 30 -> CENA
                else -> PICOTEO
            }
        }

        fun fromName(name: String?): Meal = entries.firstOrNull { it.name == name } ?: PICOTEO
    }
}
