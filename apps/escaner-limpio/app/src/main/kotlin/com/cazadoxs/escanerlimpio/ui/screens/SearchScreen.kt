package com.cazadoxs.escanerlimpio.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.classify.TextTools
import com.cazadoxs.escanerlimpio.data.DocEntry
import com.cazadoxs.escanerlimpio.ui.LocalActions
import com.cazadoxs.escanerlimpio.ui.Routes
import com.cazadoxs.escanerlimpio.ui.app
import com.cazadoxs.escanerlimpio.ui.components.DocRow
import com.cazadoxs.escanerlimpio.ui.theme.LocalBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Hit(val doc: DocEntry, val snippet: AnnotatedString?)

/** Full-text search over titles and every word the OCR read, accent-insensitive. */
@Composable
fun SearchScreen() {
    val app = app()
    val actions = LocalActions.current
    val library by app.store.library.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Category?>(null) }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    val focus = remember { FocusRequester() }
    val highlight = LocalBrand.current.highlighter

    val haystacks by produceState(emptyMap<String, String>(), library) {
        value = withContext(Dispatchers.Default) {
            library.docs.associate { d ->
                d.id to TextTools.normalize(d.title + " " + d.issuer.orEmpty() + " " + d.category.label + " " + d.text)
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query, filter, haystacks) {
        val terms = TextTools.queryTerms(query)
        hits = withContext(Dispatchers.Default) {
            library.docs.asSequence()
                .filter { filter == null || it.category == filter }
                .mapNotNull { d ->
                    if (terms.isEmpty()) return@mapNotNull if (filter != null) Hit(d, null) else null
                    val hay = haystacks[d.id] ?: return@mapNotNull null
                    if (terms.all { it in hay }) Hit(d, snippet(d.text, terms.first(), highlight)) else null
                }
                .sortedByDescending { it.doc.createdAt }
                .take(200)
                .toList()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
            TextField(
                value = query,
                onValueChange = { query = it.take(100) },
                placeholder = { Text("Busca «Iberdrola», «IBAN», «2025»…") },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Borrar búsqueda") } },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Category.entries.forEach { c ->
                FilterChip(selected = filter == c, onClick = { filter = if (filter == c) null else c }, label = { Text(c.label) })
            }
        }
        if (query.isNotBlank() && hits.isEmpty()) {
            Text(
                "Nada coincide. La búsqueda mira el nombre y todo el texto de cada página.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(hits, key = { it.doc.id }) { h ->
                Column(Modifier.fillMaxWidth()) {
                    DocRow(h.doc, false, onClick = { actions.nav.navigate(Routes.doc(h.doc.id)) }, onLongClick = {})
                    h.snippet?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.padding(start = 74.dp, end = 8.dp, bottom = 8.dp))
                    }
                }
            }
        }
    }
}

/** A short excerpt of the OCR text around the first match, with the match highlighted. */
private fun snippet(text: String, term: String, color: Color): AnnotatedString? {
    if (text.isBlank()) return null
    val norm = TextTools.normalize(text)
    val at = norm.indexOf(term)
    if (at < 0) return null
    // normalize() collapses whitespace, so map back through a whitespace-collapsed original.
    val flat = text.replace(Regex("\\s+"), " ").trim()
    if (flat.length != norm.length) return AnnotatedString("…" + flat.take(120))
    val start = (at - 40).coerceAtLeast(0)
    val end = (at + term.length + 60).coerceAtMost(flat.length)
    return buildAnnotatedString {
        if (start > 0) append("…")
        append(flat.substring(start, at))
        withStyle(SpanStyle(background = color.copy(alpha = 0.45f), fontWeight = FontWeight.Bold)) { append(flat.substring(at, at + term.length)) }
        append(flat.substring(at + term.length, end))
        if (end < flat.length) append("…")
    }
}
