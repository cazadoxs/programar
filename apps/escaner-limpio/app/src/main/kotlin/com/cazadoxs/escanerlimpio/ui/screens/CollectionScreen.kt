package com.cazadoxs.escanerlimpio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.data.DocEntry
import com.cazadoxs.escanerlimpio.ui.LocalActions
import com.cazadoxs.escanerlimpio.ui.Routes
import com.cazadoxs.escanerlimpio.ui.app
import com.cazadoxs.escanerlimpio.ui.components.DocRow
import com.cazadoxs.escanerlimpio.util.Share
import kotlinx.coroutines.launch

private enum class Sort(val label: String) { DOC_DATE("Fecha del documento"), SCANNED("Fecha de escaneo"), NAME("Nombre") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(kind: String, key: String) {
    val app = app()
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by app.store.library.collectAsStateWithLifecycle()
    val isPro by app.billing.isPro.collectAsStateWithLifecycle()

    val category = if (kind == "c") Category.fromKey(key) else null
    val folder = if (kind == "f") library.folders.firstOrNull { it.id == key } else null
    val title = category?.label ?: folder?.name ?: "Carpeta"

    var sort by remember { mutableStateOf(Sort.DOC_DATE) }
    var sortMenu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    val selected: SnapshotStateList<String> = remember { emptyList<String>().toMutableStateList() }
    var dialog by remember { mutableStateOf<String?>(null) }

    val docs = remember(library, sort, kind, key) {
        library.docs.filter { if (category != null) it.category == category else it.folderId == key }.let { list ->
            when (sort) {
                Sort.DOC_DATE -> list.sortedByDescending { it.effectiveDate }
                Sort.SCANNED -> list.sortedByDescending { it.createdAt }
                Sort.NAME -> list.sortedBy { it.title.lowercase() }
            }
        }
    }
    val selection = docs.filter { it.id in selected }
    BackHandler(selected.isNotEmpty()) { selected.clear() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (selected.isEmpty()) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primaryContainer,
                ),
                navigationIcon = {
                    if (selected.isEmpty()) {
                        IconButton(onClick = { actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                    } else {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Filled.Close, "Cancelar selección") }
                    }
                },
                title = { Text(if (selected.isEmpty()) title else "${selected.size} seleccionados") },
                actions = {
                    if (selected.isEmpty()) {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, "Ordenar") }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                            Sort.entries.forEach { s ->
                                DropdownMenuItem(text = { Text((if (s == sort) "✓ " else "") + s.label) }, onClick = { sort = s; sortMenu = false })
                            }
                        }
                        if (folder != null) {
                            IconButton(onClick = { folderMenu = true }) { Icon(Icons.Outlined.MoreVert, "Más") }
                            DropdownMenu(folderMenu, onDismissRequest = { folderMenu = false }) {
                                DropdownMenuItem(text = { Text("Renombrar carpeta") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { folderMenu = false; dialog = "renombrar" })
                                DropdownMenuItem(text = { Text("Borrar carpeta") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { folderMenu = false; dialog = "borrarCarpeta" })
                            }
                        }
                    } else {
                        IconButton(onClick = { scope.launch { Share.shareDocs(context, app.store, selection) } }) { Icon(Icons.Outlined.Share, "Compartir") }
                        IconButton(onClick = {
                            when {
                                selection.size < 2 -> actions.snackbar("Elige al menos dos documentos para unirlos.")
                                !isPro -> actions.nav.navigate(Routes.PRO)
                                else -> dialog = "unir"
                            }
                        }) { Icon(Icons.Outlined.CallMerge, "Unir") }
                        IconButton(onClick = { dialog = "mover" }) { Icon(Icons.Outlined.DriveFileMove, "Mover") }
                        IconButton(onClick = { dialog = "borrar" }) { Icon(Icons.Outlined.Delete, "Borrar") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (selected.isEmpty()) {
                FloatingActionButton(onClick = { actions.scan(folder?.id) }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Outlined.DocumentScanner, "Escanear aquí")
                }
            }
        },
    ) { padding ->
        if (docs.isEmpty()) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text("Aquí no hay nada todavía", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (category != null) "Cuando escanees algo de tipo ${category.label.lowercase()}, aparecerá aquí solo."
                    else "Escanea desde aquí o mueve documentos a esta carpeta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding(), bottom = 100.dp)) {
            items(docs, key = { it.id }) { d ->
                DocRow(
                    d,
                    selected = d.id in selected,
                    showCategory = category == null,
                    onClick = {
                        if (selected.isEmpty()) actions.nav.navigate(Routes.doc(d.id))
                        else if (d.id in selected) selected.remove(d.id) else selected.add(d.id)
                    },
                    onLongClick = { if (d.id !in selected) selected.add(d.id) },
                )
            }
        }
    }

    when (dialog) {
        "renombrar" -> TextInputDialog("Renombrar carpeta", folder?.name.orEmpty(), "Guardar", { dialog = null }, { name ->
            dialog = null
            folder?.let { scope.launch { app.store.renameFolder(it.id, name) } }
        })
        "borrarCarpeta" -> ConfirmDialog("¿Borrar la carpeta?", "Los documentos no se borran: siguen en sus carpetas inteligentes.", "Borrar carpeta", { dialog = null }) {
            dialog = null
            folder?.let { scope.launch { app.store.deleteFolder(it.id) } }
            actions.nav.popBackStack()
        }
        "borrar" -> ConfirmDialog("¿Borrar ${selection.size} documentos?", "Se borrarán de este móvil. No se puede deshacer.", "Borrar", { dialog = null }) {
            dialog = null
            val ids = selection.map { it.id }
            selected.clear()
            scope.launch { app.store.delete(ids) }
        }
        "mover" -> MoveDialog(library.folders, onDismiss = { dialog = null }) { folderId ->
            dialog = null
            val ids = selection.map { it.id }
            selected.clear()
            scope.launch { ids.forEach { id -> app.store.update(id) { it.copy(folderId = folderId) } } }
        }
        "unir" -> TextInputDialog("Unir en un PDF", selection.firstOrNull()?.title.orEmpty() + " (completo)", "Unir", { dialog = null }, { name ->
            dialog = null
            // Keep the order in which documents were tapped.
            val ordered = selected.toList()
            selected.clear()
            app.service.merge(ordered, name)
        })
    }
}

@Composable
fun MoveDialog(folders: List<com.cazadoxs.escanerlimpio.data.Folder>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    ChoiceDialog(
        title = "Mover a carpeta",
        choices = listOf(Choice<String?>(null, "Ninguna (solo carpeta inteligente)")) + folders.map { Choice<String?>(it.id, it.name) },
        selected = null,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

