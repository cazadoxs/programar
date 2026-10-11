package io.github.cazadoxs.toma.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.data.ProfileEntity
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.TomaTopBar
import io.github.cazadoxs.toma.ui.components.swatch

private val Emojis = listOf("🙂", "👵", "👴", "👩", "👨", "🧒", "👧", "👦", "🐶", "🐱", "❤️", "⭐")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProfileEntity?>(null) }
    var lastError by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TomaTopBar(stringResource(R.string.settings_profiles), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.profile_new)) })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(profiles, key = { it.id }) { p ->
                LabelCard(accent = swatch(p.colorIndex), onClick = { editing = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.padding(8.dp))
                        Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { deleting = p }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (lastError) item { Text(stringResource(R.string.profile_last), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (creating || editing != null) {
        val base = editing
        var name by remember(base) { mutableStateOf(base?.name ?: "") }
        var emoji by remember(base) { mutableStateOf(base?.emoji ?: "🙂") }
        var color by remember(base) { mutableStateOf(base?.colorIndex ?: profiles.size % 8) }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(stringResource(if (base == null) R.string.profile_new else R.string.profile_edit)) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.profile_name)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Emojis.forEach { e -> FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    if (base == null) vm.addProfile(name, emoji, color) else vm.updateProfile(base.copy(name = name, emoji = emoji, colorIndex = color))
                    creating = false; editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.profile_delete_title, p.name)) },
            text = { Text(stringResource(R.string.profile_delete_body)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteProfile(p) { ok -> lastError = !ok }; deleting = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
