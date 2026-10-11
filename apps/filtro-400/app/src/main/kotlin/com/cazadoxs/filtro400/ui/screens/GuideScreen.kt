package com.cazadoxs.filtro400.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.filtro400.ui.AppViewModel
import com.cazadoxs.filtro400.ui.components.Gap
import com.cazadoxs.filtro400.ui.components.Panel
import com.cazadoxs.filtro400.ui.components.ScreenHeader
import com.cazadoxs.filtro400.ui.components.ScrollScreen
import com.cazadoxs.filtro400.ui.components.SectionLabel
import com.cazadoxs.filtro400.ui.theme.Mono

@Composable
fun GuideScreen(vm: AppViewModel, onBack: () -> Unit) {
    val flags by vm.flags.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ScrollScreen {
        ScreenHeader("Tus armas contra el spam", "Filtro 400 es una capa. Estas tres son gratis y se suman.", onBack = onBack)

        SectionLabel("1 · Lista Robinson")
        Panel {
            Text(
                "Es el servicio oficial y gratuito de exclusión publicitaria. Si estás inscrito, las empresas no pueden llamarte con publicidad salvo que les hayas dado permiso. Tarda unos minutos y es efectivo a partir de los dos meses.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Gap(8.dp)
            Step(1, "Entra en listarobinson.es y regístrate.")
            Step(2, "Añade el canal \"Teléfono\" con tus números fijos y móviles.")
            Step(3, "Confirma desde el correo que te envían.")
            Gap(10.dp)
            Button(onClick = { open(context, "https://www.listarobinson.es/") }, modifier = Modifier.fillMaxWidth()) {
                Text("Ir a la Lista Robinson")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = flags?.robinsonDone == true, onCheckedChange = vm::setRobinsonDone)
                Text("Ya estoy inscrito", style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionLabel("2 · Pide a tu operador que bloquee el 400")
        Panel {
            Text(
                "Con el nuevo rango 400, los operadores deben permitirte desconectar las llamadas comerciales que vengan de él. Así se cortan en la red, incluso con el móvil apagado o en otro teléfono de casa.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Gap(8.dp)
            Text(
                "Llama a atención al cliente de tu operador o búscalo en su app como \"bloqueo de llamadas comerciales\" o \"desconexión del 400\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("3 · Denuncia a quien se salte las normas")
        Panel {
            Text(
                "Si una empresa te llama para venderte algo desde un número que no es 400, o te llama estando en la Lista Robinson sin tu permiso, puedes reclamar ante la Agencia Española de Protección de Datos. Anota el número, la fecha, la hora y la empresa.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Gap(10.dp)
            OutlinedButton(onClick = { open(context, "https://www.aepd.es/") }, modifier = Modifier.fillMaxWidth()) {
                Text("Ir a la AEPD")
            }
        }

        SectionLabel("Qué es el 400")
        Panel {
            Text(
                "Desde el 17 de octubre de 2026, las empresas sujetas a la Ley 10/2025 de servicios de atención a la clientela solo pueden hacer llamadas comerciales desde números de 9 cifras que empiezan por 400. Ese rango no se puede usar para atención al cliente, ni para recibir llamadas.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Gap(8.dp)
            Text("400 XXX XXX", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Mono))
        }
        Gap()
        Text(
            "Información orientativa, no es asesoramiento legal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$n", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono), color = MaterialTheme.colorScheme.primary)
        Text("  $text", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun open(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}
