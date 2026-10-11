package com.cazadoxs.escanerlimpio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.classify.DocumentAnalyzer
import com.cazadoxs.escanerlimpio.classify.TextTools
import com.cazadoxs.escanerlimpio.security.LockGuard
import com.cazadoxs.escanerlimpio.ui.LocalActions
import com.cazadoxs.escanerlimpio.ui.Routes
import com.cazadoxs.escanerlimpio.ui.app
import com.cazadoxs.escanerlimpio.ui.components.CategoryChip
import com.cazadoxs.escanerlimpio.ui.components.Pill
import com.cazadoxs.escanerlimpio.ui.components.ProBadge
import com.cazadoxs.escanerlimpio.ui.components.icon
import com.cazadoxs.escanerlimpio.ui.theme.LocalBrand
import com.cazadoxs.escanerlimpio.util.Format
import com.cazadoxs.escanerlimpio.util.Share
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(id: String) {
    val app = app()
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by app.store.library.collectAsStateWithLifecycle()
    val isPro by app.billing.isPro.collectAsStateWithLifecycle()
    val doc = library.docs.firstOrNull { it.id == id }
    if (doc == null) {
        LaunchedEffect(Unit) { actions.nav.popBackStack() }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var zoomPage by remember { mutableStateOf<Int?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            runCatching { Share.exportTo(context, app.store, doc, uri) }
                .onSuccess { actions.snackbar("PDF guardado") }
                .onFailure { actions.snackbar("No se pudo guardar el PDF") }
        }
    }

    val source = rememberPageSource(app.store.pdfFile(doc.id), "${doc.sizeBytes}:${doc.pageCount}")

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { dialog = "renombrar" }) },
                actions = {
                    IconButton(onClick = { scope.launch { app.store.update(doc.id) { it.copy(favorite = !it.favorite) } } }) {
                        if (doc.favorite) Icon(Icons.Filled.Star, "Quitar de favoritos", tint = LocalBrand.current.highlighter)
                        else Icon(Icons.Outlined.StarBorder, "Añadir a favoritos")
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; dialog = "renombrar" })
                        DropdownMenuItem(text = { Text("Cambiar tipo") }, leadingIcon = { Icon(Icons.Outlined.Category, null) }, onClick = { menu = false; dialog = "tipo" })
                        DropdownMenuItem(text = { Text("Mover a carpeta") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; dialog = "mover" })
                        DropdownMenuItem(
                            text = { Text("Organizar páginas") },
                            leadingIcon = { Icon(Icons.Outlined.GridView, null) },
                            trailingIcon = if (isPro) null else ({ ProBadge() }),
                            onClick = { menu = false; actions.nav.navigate(if (isPro) Routes.pages(doc.id) else Routes.PRO) },
                        )
                        DropdownMenuItem(text = { Text("Guardar en el móvil…") }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = {
                            menu = false
                            LockGuard.expectExternalFlow()
                            exporter.launch(TextTools.safeFileName(doc.title) + ".pdf")
                        })
                        DropdownMenuItem(text = { Text("Borrar") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; dialog = "borrar" })
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                FilledTonalButton(onClick = { scope.launch { Share.shareDocs(context, app.store, listOf(doc)) } }, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Compartir PDF")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { dialog = "texto" }) { Icon(Icons.Outlined.TextSnippet, "Ver texto") }
                IconButton(onClick = {
                    if (doc.text.isBlank()) actions.snackbar("No se detectó texto en este documento.")
                    else { Share.copyText(context, doc.text); actions.snackbar("Texto copiado") }
                }) { Icon(Icons.Outlined.ContentCopy, "Copiar texto") }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }.coerceIn(200, 1600)
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryChip(doc.category, Modifier.clickable { dialog = "tipo" })
                        Pill(DocumentAnalyzer.fullDateLabel(doc.effectiveDate))
                        doc.amount?.let { Pill(Format.money(it)) }
                        Pill(Format.pages(doc.pageCount))
                        Pill(Format.size(doc.sizeBytes))
                    }
                }
                items(source.pageCount) { index ->
                    PageImage(source, index, widthPx, onClick = { zoomPage = index })
                }
            }
        }
    }

    zoomPage?.let { page ->
        ZoomDialog(source, page, onDismiss = { zoomPage = null })
    }

    when (dialog) {
        "renombrar" -> TextInputDialog("Renombrar", doc.title, "Guardar", { dialog = null }, { name ->
            dialog = null
            scope.launch { app.store.update(doc.id) { it.copy(title = name.take(120)) } }
        })
        "tipo" -> ChoiceDialog(
            "Carpeta inteligente",
            Category.entries.map { Choice(it, it.label, it.icon) },
            selected = doc.category,
            onDismiss = { dialog = null },
        ) { c ->
            dialog = null
            scope.launch { app.store.update(doc.id) { it.copy(category = c) } }
        }
        "mover" -> MoveDialog(library.folders, onDismiss = { dialog = null }) { folderId ->
            dialog = null
            scope.launch { app.store.update(doc.id) { it.copy(folderId = folderId) } }
        }
        "borrar" -> ConfirmDialog("¿Borrar este documento?", "Se borrará de este móvil. No se puede deshacer.", "Borrar", { dialog = null }) {
            dialog = null
            scope.launch { app.store.delete(listOf(doc.id)) }
            actions.nav.popBackStack()
        }
        "texto" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Texto reconocido") },
            text = {
                SelectionContainer(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
                    Text(doc.text.ifBlank { "No se detectó texto." }, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cerrar") } },
            dismissButton = {
                TextButton(onClick = { Share.shareText(context, doc.title, doc.text) }, enabled = doc.text.isNotBlank()) { Text("Compartir") }
            },
        )
    }
}

@Composable
private fun PageImage(source: PageSource, index: Int, widthPx: Int, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, source, index, widthPx) { value = source.render(index, widthPx) }
    val bmp = bitmap
    val ratio = bmp?.let { it.width.toFloat() / it.height } ?: (1f / 1.414f)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .shadow(3.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
    ) {
        if (bmp != null) Image(bmp, "Página ${index + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ZoomDialog(source: PageSource, index: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val bitmap by produceState<ImageBitmap?>(null, source, index) { value = source.render(index, 2000) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            bitmap?.let {
                Image(
                    it,
                    "Página ${index + 1} ampliada",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(16.dp)) {
                Text("Cerrar", color = Color.White)
            }
        }
    }
}
