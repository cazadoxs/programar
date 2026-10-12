package com.cazadoxs.descifra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cazadoxs.descifra.core.rules.QuoteLocator

/**
 * The original letter text, exactly as read, with the fragments the summary relies on
 * highlighted. The summary never replaces it: both are always one tap (or one pane) apart.
 */
@Composable
fun OriginalText(
    text: String,
    quotes: List<String?>,
    focus: String?,
    modifier: Modifier = Modifier,
) {
    val p = MaterialTheme.palette
    val ranges = remember(text, quotes) { QuoteLocator.findAll(text, quotes) }
    val focusRange = remember(text, focus) { QuoteLocator.find(text, focus) }
    val annotated: AnnotatedString = remember(text, ranges, focusRange, p) {
        buildAnnotatedString {
            var i = 0
            val all = (ranges + listOfNotNull(focusRange)).sortedBy { it.first }
            for (r in all) {
                if (r.first < i) continue
                append(text.substring(i, r.first))
                val strong = focusRange != null && r.first <= focusRange.first && r.last >= focusRange.last
                withStyle(SpanStyle(background = (if (strong) p.stamp else p.amber).copy(alpha = if (strong) 0.30f else 0.22f), fontWeight = FontWeight.Bold)) {
                    append(text.substring(r.first, r.last + 1))
                }
                i = r.last + 1
            }
            if (i < text.length) append(text.substring(i))
        }
    }
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(focusRange, layout) {
        val l = layout ?: return@LaunchedEffect
        val r = focusRange ?: return@LaunchedEffect
        val line = l.getLineForOffset(r.first.coerceIn(0, text.length))
        scroll.animateScrollTo((l.getLineTop(line) - 40).toInt().coerceAtLeast(0))
    }
    Column(modifier.verticalScroll(scroll).padding(16.dp)) {
        Text(
            annotated,
            style = MaterialTheme.typography.bodyMedium,
            color = p.ink,
            onTextLayout = { layout = it },
        )
    }
}

@Composable
fun OriginalHeader(modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val p = MaterialTheme.palette
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow("Carta original")
            Text("Lo que dice el papel, sin resumir", color = p.inkSoft, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}
