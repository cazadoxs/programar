package io.github.cazadoxs.toma.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.Permissions
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: Int, val body: Int)

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val pages = listOf(
        Page(Icons.Rounded.AllInclusive, R.string.onb_title_1, R.string.onb_body_1),
        Page(Icons.Rounded.NotificationsActive, R.string.onb_title_2, R.string.onb_body_2),
        Page(Icons.Rounded.Lock, R.string.onb_title_3, R.string.onb_body_3),
    )
    val pager = rememberPagerState(pageCount = { pages.size + 1 })
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            if (index < pages.size) {
                val p = pages[index]
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(132.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(p.icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.height(36.dp))
                    Text(stringResource(p.title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(p.body), style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.onb_your_name), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.onb_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.onb_disclaimer), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size + 1) { i ->
                Box(
                    Modifier.padding(4.dp).size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
        val last = pager.currentPage == pages.size
        Button(
            onClick = {
                if (!last) {
                    // Ask for notifications while explaining alarms (page 2).
                    if (pager.currentPage == 1 && Build.VERSION.SDK_INT >= 33 && !Permissions.check(context).notifications) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                } else {
                    vm.finishOnboarding(name.trim().ifBlank { context.getString(R.string.onb_default_name) })
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
        ) { Text(stringResource(if (last) R.string.onb_start else R.string.next)) }
        TextButton(
            onClick = { vm.finishOnboarding(name.trim().ifBlank { context.getString(R.string.onb_default_name) }) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(stringResource(R.string.skip)) }
    }
}
