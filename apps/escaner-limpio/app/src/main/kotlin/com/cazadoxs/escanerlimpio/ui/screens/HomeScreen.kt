package com.cazadoxs.escanerlimpio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.ui.LocalActions
import com.cazadoxs.escanerlimpio.ui.Routes
import com.cazadoxs.escanerlimpio.ui.app
import com.cazadoxs.escanerlimpio.ui.components.CategoryTile
import com.cazadoxs.escanerlimpio.ui.components.DocRow
import com.cazadoxs.escanerlimpio.ui.components.ProBadge
import com.cazadoxs.escanerlimpio.ui.components.SectionTitle
import com.cazadoxs.escanerlimpio.ui.components.highlight
import com.cazadoxs.escanerlimpio.ui.components.paper
import com.cazadoxs.escanerlimpio.ui.theme.LocalBrand
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val PINNED = listOf(Category.IDENTIDAD, Category.NOMINAS, Category.FACTURAS, Category.TICKETS, Category.HACIENDA, Category.SALUD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val app = app()
    val actions = LocalActions.current
    val library by app.store.library.collectAsStateWithLifecycle()
    val isPro by app.billing.isPro.collectAsStateWithLifecycle()
    val docs = library.docs
    val counts = remember(docs) { docs.groupingBy { it.category }.eachCount() }
    val categories = remember(counts) {
        (PINNED + Category.entries.filter { (counts[it] ?: 0) > 0 }).distinct()
    }
    var newFolder by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { actions.scan(null) },
                icon = { Icon(Icons.Outlined.DocumentScanner, null) },
                text = { Text("Escanear") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 120.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Escáner", style = MaterialTheme.typography.headlineLarge)
                        Text("limpio.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.highlight(LocalBrand.current.highlighter))
                    }
                    IconButton(onClick = { actions.nav.navigate(Routes.SEARCH) }) { Icon(Icons.Outlined.Search, "Buscar") }
                    IconButton(onClick = { actions.nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Ajustes") }
                }
            }
            item { PromiseCard(docs.size, docs.sumOf { it.pageCount }) }
            item {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { actions.pickPhotos(null) }, label = { Text("Desde fotos") }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)) })
                    AssistChip(
                        onClick = { actions.pickPdf(null) },
                        label = { Text("Importar PDF") },
                        leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp)) },
                        trailingIcon = if (isPro) null else ({ ProBadge() }),
                    )
                }
            }

            item { SectionTitle("Carpetas inteligentes") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categories.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c ->
                                CategoryTile(c, counts[c] ?: 0, onClick = { actions.nav.navigate(Routes.category(c)) }, modifier = Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            item { RentaBanner(isPro) { actions.nav.navigate(if (isPro) Routes.RENTA else Routes.PRO) } }

            item {
                SectionTitle("Mis carpetas") {
                    TextButton(onClick = { newFolder = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nueva")
                    }
                }
            }
            if (library.folders.isEmpty()) {
                item {
                    Text(
                        "Crea carpetas propias (la casa, el coche, un trámite…). Los documentos siguen apareciendo también en su carpeta inteligente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(library.folders, key = { "f" + it.id }) { f ->
                val n = docs.count { it.folderId == f.id }
                Surface(
                    onClick = { actions.nav.navigate(Routes.folder(f.id)) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Text(f.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            val favorites = docs.filter { it.favorite }
            if (favorites.isNotEmpty()) {
                item { SectionTitle("Favoritos") }
                items(favorites.take(5), key = { "fav" + it.id }) { d ->
                    DocRow(d, false, onClick = { actions.nav.navigate(Routes.doc(d.id)) }, onLongClick = {})
                }
            }

            item { SectionTitle("Recientes") }
            if (docs.isEmpty()) {
                item { EmptyLibrary() }
            }
            items(docs.sortedByDescending { it.createdAt }.take(30), key = { it.id }) { d ->
                DocRow(d, false, onClick = { actions.nav.navigate(Routes.doc(d.id)) }, onLongClick = {})
            }
        }
    }

    if (newFolder) {
        TextInputDialog(
            title = "Nueva carpeta",
            initial = "",
            confirm = "Crear",
            onDismiss = { newFolder = false },
            onConfirm = { name ->
                newFolder = false
                if (name.isNotBlank()) scope.launch { app.store.createFolder(name) }
            },
        )
    }
}

@Composable
private fun PromiseCard(docCount: Int, pageCount: Int) {
    val brand = LocalBrand.current
    Box(Modifier.padding(top = 16.dp).fillMaxWidth().paper(fold = 26.dp, radius = 22.dp, elevation = 3.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (docCount == 0) "Tus papeles, limpios y en orden." else "$docCount documentos, $pageCount páginas.",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("0 marcas de agua", "0 anuncios", "Sin Internet").forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = brand.ink,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(brand.highlighter.copy(alpha = 0.35f)).padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RentaBanner(isPro: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Savings, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Paquete Renta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    if (!isPro) {
                        Spacer(Modifier.width(8.dp))
                        ProBadge()
                    }
                }
                Text(
                    "Nóminas, certificados y facturas del año en un ZIP listo para tu gestor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 90.dp, height = 116.dp).paper(fold = 20.dp, radius = 10.dp, elevation = 6.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Star, null, tint = LocalBrand.current.highlighter, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Aún no hay documentos", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pulsa Escanear y apunta a un DNI, una nómina o un ticket. Se guardará con su nombre y en su carpeta.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
