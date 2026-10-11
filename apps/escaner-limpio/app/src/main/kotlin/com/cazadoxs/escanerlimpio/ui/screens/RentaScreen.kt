package com.cazadoxs.escanerlimpio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.security.LockGuard
import com.cazadoxs.escanerlimpio.ui.LocalActions
import com.cazadoxs.escanerlimpio.ui.app
import com.cazadoxs.escanerlimpio.ui.components.icon
import com.cazadoxs.escanerlimpio.util.Format
import java.time.LocalDate

/** Pro: everything for one tax year, ready for the accountant or Renta Web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentaScreen() {
    val app = app()
    val actions = LocalActions.current
    val library by app.store.library.collectAsStateWithLifecycle()
    val isPro by app.billing.isPro.collectAsStateWithLifecycle()
    if (!isPro) {
        LaunchedEffect(Unit) { actions.nav.popBackStack() }
        return
    }
    val years = remember(library) {
        (library.docs.map { it.effectiveDate.year } + (LocalDate.now().year - 1)).distinct().sortedDescending()
    }
    var year by remember { mutableIntStateOf(LocalDate.now().year - 1) }
    var cats by remember { mutableStateOf(Category.RENTA_DEFAULT) }
    val docs = remember(library, year, cats) { app.service.rentaDocs(year, cats) }

    val zipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) app.service.exportRentaZip(uri, year, cats)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text("Paquete Renta") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = 48.dp)) {
            item {
                Text(
                    "Reúne los papeles de un ejercicio en un ZIP ordenado por carpetas, con un resumen de importes. Ideal para tu gestor o para tener todo a mano en Renta Web.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text("Ejercicio", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.forEach { y -> FilterChip(selected = y == year, onClick = { year = y }, label = { Text("$y") }) }
                }
                Text("Incluir", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            }
            items(Category.entries.filter { it != Category.OTROS && it != Category.IDENTIDAD }) { c ->
                val n = library.docs.count { it.category == c && it.effectiveDate.year == year }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = c in cats, onCheckedChange = { on -> cats = if (on) cats + c else cats - c })
                    Icon(c.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(c.label, modifier = Modifier.weight(1f))
                    Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("${docs.size} documentos de $year", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        docs.groupBy { it.category }.forEach { (c, list) ->
                            val total = list.mapNotNull { it.amount }.sum()
                            Text(
                                "${c.label}: ${list.size}" + if (total > 0) " · ${Format.money(total)}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Text(
                            "Los importes se leen del texto y pueden no ser exactos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Button(
                    enabled = docs.isNotEmpty(),
                    onClick = {
                        LockGuard.expectExternalFlow()
                        zipSaver.launch("Renta $year.zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.FolderZip, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar ZIP")
                }
                OutlinedButton(
                    enabled = docs.size >= 2,
                    onClick = { app.service.merge(docs.map { it.id }, "Renta $year (todo)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Outlined.CallMerge, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unir todo en un PDF")
                }
            }
        }
    }
}
