package app.apunta

import app.apunta.data.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test fun buildsPrefixTerms() {
        assertEquals("\"reunión*\" \"pres*\"", SearchQuery.toFtsMatch("reunión pres"))
    }

    @Test fun stripsOperatorsAndQuotes() {
        assertEquals("\"foo*\" \"bar*\"", SearchQuery.toFtsMatch("\"foo\" AND bar*"))
        assertNull(SearchQuery.toFtsMatch("  \" ( ) * "))
        assertNull(SearchQuery.toFtsMatch("OR NOT"))
    }

    @Test fun limitsTermCount() {
        val q = (1..40).joinToString(" ") { "w$it" }
        assertEquals(12, SearchQuery.toFtsMatch(q)!!.split(" ").size)
    }

    @Test fun escapesLike() {
        assertEquals("50\\% \\_x \\\\", SearchQuery.escapeLike("50% _x \\"))
    }

    @Test fun highlightIgnoresAccentsAndCase() {
        val r = SearchQuery.highlightRanges("El Camión llegó", "camion")
        assertEquals(listOf(3..8), r)
    }

    @Test fun highlightMergesOverlaps() {
        val r = SearchQuery.highlightRanges("abcdef", "abc cde")
        assertEquals(listOf(0..4), r)
    }
}
