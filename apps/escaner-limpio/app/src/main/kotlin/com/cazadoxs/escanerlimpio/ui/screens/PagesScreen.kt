package com.cazadoxs.escanerlimpio.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escanerlimpio.ui.LocalActions
import com.cazadoxs.escanerlimpio.ui.app

/** Pro: reorder, rotate, delete and extract pages. Changes apply on "Guardar". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(id: String) {
    val app = app()
    val actions = LocalActions.current
    val library by app.store.library.collectAsStateWithLifecycle()
    val isPro by app.billing.isPro.collectAsStateWithLifecycle()
    val doc = library.docs.firstOrNull { it.id == id }
    if (doc == null || !isPro) {
        LaunchedEffect(Unit) { actions.nav.popBackStack() }
        return
    }
    val source = rememberPageSource(app.store.pdfFile(doc.id), "${doc.sizeBytes}:${doc.pageCount}")
    // Working copy: original page indexes in their new order.
    val order = remember(source) { mutableStateListOf<Int>().apply { addAll(0 until source.pageCount) } }
    val rotations = remember(source) { mutableStateMapOf<Int, Int>() }
    val selected = remember(source) { mutableStateListOf<Int>() }
    var extractDialog by remember { mutableStateOf(false) }
    val changed = order.toList() != (0 until source.pageCount).toList() || rotations.values.any { it % 360 != 0 }

    fun move(delta: Int) {
        val positions = selected.map { order.indexOf(it) }.sorted().let { if (delta > 0) it.reversed() else it }
        for (p in positions) {
            val q = p + delta
            if (q !in order.indices || order[q] in selected) continue
            val tmp = order[q]; order[q] = order[p]; order[p] = tmp
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text(if (selected.isEmpty()) "Organizar páginas" else "${selected.size} seleccionadas") },
                actions = {
                    TextButton(
                        enabled = changed && order.isNotEmpty(),
                        onClick = {
                            app.service.editPages(doc.id, order.toList(), rotations.toMap())
                            actions.nav.popBackStack()
                        },
                    ) { Text("Guardar") }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                val has = selected.isNotEmpty()
                IconButton(enabled = has, onClick = { move(-1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Mover antes") }
                IconButton(enabled = has, onClick = { move(1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Mover después") }
                IconButton(enabled = has, onClick = { selected.forEach { rotations[it] = ((rotations[it] ?: 0) + 90) % 360 } }) {
                    Icon(Icons.Outlined.RotateRight, "Girar")
                }
                IconButton(enabled = has && selected.size < order.size, onClick = {
                    order.removeAll(selected.toSet()); selected.clear()
                }) { Icon(Icons.Outlined.Delete, "Quitar páginas") }
                IconButton(enabled = has, onClick = { extractDialog = true }) { Icon(Icons.Outlined.ContentCut, "Separar en otro documento") }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(order, key = { _, page -> page }) { position, page ->
                val bmp by produceState<ImageBitmap?>(null, source, page) { value = source.render(page, 360) }
                val isSel = page in selected
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(3.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { if (isSel) selected.remove(page) else selected.add(page) },
                    contentAlignment = Alignment.Center,
                ) {
                    bmp?.let {
                        Image(it, "Página ${page + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp).rotate((rotations[page] ?: 0).toFloat()))
                    }
                    Text(
                        "${position + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    if (isSel) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
        }
    }

    if (extractDialog) {
        TextInputDialog("Separar ${selected.size} páginas", doc.title + " (págs.)", "Crear documento", { extractDialog = false }, { name ->
            extractDialog = false
            val pages = order.filter { it in selected }
            selected.clear()
            app.service.extractPages(doc.id, pages, name)
        })
    }
}
