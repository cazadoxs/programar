package com.cazadoxs.filtro400.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.filtro400.billing.BillingManager
import com.cazadoxs.filtro400.billing.Plan
import com.cazadoxs.filtro400.ui.AppViewModel
import com.cazadoxs.filtro400.ui.MainActivity
import com.cazadoxs.filtro400.ui.components.Gap
import com.cazadoxs.filtro400.ui.components.HGap
import com.cazadoxs.filtro400.ui.components.Pill
import com.cazadoxs.filtro400.ui.components.ScreenHeader
import com.cazadoxs.filtro400.ui.components.ScrollScreen
import com.cazadoxs.filtro400.ui.theme.LocalStatus

@Composable
fun PremiumScreen(vm: AppViewModel, host: MainActivity, onBack: () -> Unit) {
    val flags by vm.flags.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val available by vm.billingAvailable.collectAsStateWithLifecycle()
    val premium = flags?.premium == true
    val status = LocalStatus.current
    val context = LocalContext.current
    var chosen by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.connectBilling() }
    LaunchedEffect(premium) { if (premium) vm.syncCommunity() }

    ScrollScreen {
        ScreenHeader(
            if (premium) "Ya eres Premium" else "Filtro 400 Premium",
            if (premium) "Gracias por mantener viva la lista de la comunidad." else "El filtrado local es y será gratis. Premium añade la inteligencia de miles de usuarios.",
            onBack = onBack,
        )
        Gap()
        listOf(
            "Lista de la comunidad actualizada cada día con los números más denunciados en España.",
            "Pilla a los que llaman para vender desde móviles y fijos normales, saltándose el 400.",
            "Las consultas se hacen en tu teléfono: tus llamadas nunca salen de él.",
            "Sin anuncios. Ni en la versión gratuita ni en la de pago.",
        ).forEach { Benefit(it, status.allowed) }
        Gap(20.dp)

        if (!premium) {
            if (plans.isEmpty()) {
                Text(
                    if (available) "Cargando precios…" else "Google Play no está disponible en este momento. Comprueba tu conexión.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val selectedId = chosen ?: plans.firstOrNull()?.basePlanId
            plans.forEach { plan -> PlanCard(plan, plan.basePlanId == selectedId) { chosen = plan.basePlanId } }
            Gap()
            Button(
                onClick = { plans.firstOrNull { it.basePlanId == selectedId }?.let { vm.buy(host, it) } },
                enabled = plans.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Suscribirme", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = vm::restore, modifier = Modifier.fillMaxWidth()) { Text("Ya pagué: restaurar compra") }
            Text(
                "Se renueva automáticamente. Cancela cuando quieras desde Google Play, sin permanencia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Button(onClick = {
                val pkg = context.packageName.removeSuffix(".debug")
                val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=${BillingManager.PRODUCT_ID}&package=$pkg")
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) {}
            }, modifier = Modifier.fillMaxWidth()) { Text("Gestionar suscripción en Google Play") }
        }
    }
}

@Composable
private fun Benefit(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Check, null, tint = color)
        HGap()
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, onClick: () -> Unit) {
    val yearly = plan.period == "P1Y"
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (yearly) "Anual" else "Mensual", style = MaterialTheme.typography.titleMedium)
                Text(
                    plan.price + if (yearly) " al año" else " al mes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (plan.hasFreeTrial) Pill("Prueba gratis", LocalStatus.current.allowed)
            else if (yearly) Pill("Ahorra 33 %", MaterialTheme.colorScheme.primary)
        }
    }
}
