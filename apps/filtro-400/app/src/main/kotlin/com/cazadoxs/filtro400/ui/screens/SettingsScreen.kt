package com.cazadoxs.filtro400.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.filtro400.BuildConfig
import com.cazadoxs.filtro400.ui.AppViewModel
import com.cazadoxs.filtro400.ui.MainActivity
import com.cazadoxs.filtro400.ui.components.Gap
import com.cazadoxs.filtro400.ui.components.HGap
import com.cazadoxs.filtro400.ui.components.Hairline
import com.cazadoxs.filtro400.ui.components.IconBadge
import com.cazadoxs.filtro400.ui.components.KeyValueRow
import com.cazadoxs.filtro400.ui.components.Panel
import com.cazadoxs.filtro400.ui.components.Pill
import com.cazadoxs.filtro400.ui.components.ScreenHeader
import com.cazadoxs.filtro400.ui.components.ScrollScreen
import com.cazadoxs.filtro400.ui.components.SectionLabel
import com.cazadoxs.filtro400.ui.theme.LocalStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    host: MainActivity,
    onOpenPremium: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val flags by vm.flags.collectAsStateWithLifecycle()
    val communitySize by vm.communitySize.collectAsStateWithLifecycle()
    val roleHeld by host.roleHeld
    val notifications by host.notificationsGranted
    val c = config ?: return
    val status = LocalStatus.current
    val context = LocalContext.current
    val premium = flags?.premium == true

    ScrollScreen {
        ScreenHeader("Ajustes")

        SectionLabel("Estado")
        Panel {
            StatusRow("App de filtrado de llamadas", roleHeld, "Activa", "Sin activar") { host.requestScreeningRole() }
            Hairline(Modifier.padding(vertical = 12.dp))
            StatusRow("Avisos", notifications, "Permitidos", "Bloqueados") { host.askNotificationsIfNeeded() }
            if (roleHeld) {
                Gap(8.dp)
                Text(
                    "Para dejar de usar Filtro 400, elige otra app (o ninguna) en Ajustes › Aplicaciones › Aplicaciones predeterminadas › Identificación de llamadas y spam.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { openDefaultApps(context) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Abrir apps predeterminadas")
                }
            }
        }

        if (vm.communityConfigured) {
            SectionLabel("Comunidad")
            Panel(onClick = if (premium) null else onOpenPremium) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(status.allowed) { Icon(Icons.Rounded.Groups, null, tint = status.allowed) }
                    HGap()
                    Column(Modifier.weight(1f)) {
                        Text(if (premium) "Premium activo" else "Hazte Premium", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (premium) "$communitySize números denunciados en tu lista"
                            else "Lista de la comunidad actualizada a diario. Sin anuncios, siempre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!premium) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
                if (premium) {
                    Gap(10.dp)
                    val last = flags?.lastCommunitySync ?: 0
                    if (last > 0) KeyValueRow("Última actualización", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::syncCommunity) { Text("Actualizar ahora") }
                        OutlinedButton(onClick = onOpenPremium) { Text("Suscripción") }
                    }
                }
            }
        }

        SectionLabel("Avisos y registro")
        Panel {
            ToggleRow("Avisarme al filtrar", "Una notificación silenciosa con el resumen del día.", c.notifyOnBlock, vm::setNotify)
            Hairline(Modifier.padding(vertical = 12.dp))
            ToggleRow(
                "Ocultar cortadas del registro",
                "Las llamadas bloqueadas no aparecerán en el registro de la app Teléfono. Seguirán en el historial de Filtro 400.",
                c.skipCallLog, vm::setSkipCallLog,
            )
        }

        SectionLabel("Historial")
        Panel {
            Text("Guardar el historial durante", style = MaterialTheme.typography.titleMedium)
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 30, 90).forEach { d ->
                    FilterChip(selected = c.historyDays == d, onClick = { vm.setHistoryDays(d) }, label = { Text("$d días") })
                }
            }
        }

        SectionLabel("Más")
        Panel {
            LinkRow("Lista Robinson y tus derechos", onOpenGuide)
            Hairline(Modifier.padding(vertical = 4.dp))
            LinkRow("Privacidad", onOpenPrivacy)
            Hairline(Modifier.padding(vertical = 4.dp))
            LinkRow("Valorar Filtro 400") { openStore(context) }
        }

        Gap(20.dp)
        Text(
            "Filtro 400 ${BuildConfig.VERSION_NAME} · Hecho en España · Sin anuncios",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, okText: String, badText: String, onFix: () -> Unit) {
    val status = LocalStatus.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (ok) Pill(okText, status.allowed)
        else OutlinedButton(onClick = onFix) { Text("Activar") }
    }
    if (!ok) Text(badText, style = MaterialTheme.typography.bodySmall, color = status.blocked)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HGap()
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openDefaultApps(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun openStore(context: Context) {
    val pkg = context.packageName.removeSuffix(".debug")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
    }
}
