package com.cazadoxs.descifra.core.rules

import com.cazadoxs.descifra.core.model.Analysis
import java.time.LocalDate

object Reanalyze {
    /**
     * Recomputes the deadlines that depend on when the letter was received, keeping everything
     * else (including the AI summary). Literal dates read from the letter are kept as they are.
     */
    fun withReceived(analysis: Analysis, letter: String, received: LocalDate, today: LocalDate = LocalDate.now()): Analysis {
        val local = LocalAnalyzer.analyze(letter, received, today)
        val literal = analysis.deadlines.filter { !it.estimated && local.deadlines.none { l -> l.date == it.date } }
        val deadlines = (local.deadlines + literal).distinctBy { it.date to it.kind }.sortedBy { it.date }
        return analysis.copy(
            deadlines = deadlines,
            urgency = LocalAnalyzer.urgencyOf(deadlines, null, today).let { if (it < local.urgency) local.urgency else it },
        )
    }
}
