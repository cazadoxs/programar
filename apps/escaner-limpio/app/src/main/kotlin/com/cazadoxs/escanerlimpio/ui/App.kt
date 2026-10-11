package com.cazadoxs.escanerlimpio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cazadoxs.escanerlimpio.EscanerApp
import com.cazadoxs.escanerlimpio.MainActivity
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.data.Work
import com.cazadoxs.escanerlimpio.scan.Scanner
import com.cazadoxs.escanerlimpio.security.LockGuard
import com.cazadoxs.escanerlimpio.ui.components.ScanningSheet
import com.cazadoxs.escanerlimpio.ui.screens.CollectionScreen
import com.cazadoxs.escanerlimpio.ui.screens.DocumentScreen
import com.cazadoxs.escanerlimpio.ui.screens.HomeScreen
import com.cazadoxs.escanerlimpio.ui.screens.LockScreen
import com.cazadoxs.escanerlimpio.ui.screens.OnboardingScreen
import com.cazadoxs.escanerlimpio.ui.screens.PagesScreen
import com.cazadoxs.escanerlimpio.ui.screens.ProScreen
import com.cazadoxs.escanerlimpio.ui.screens.RentaScreen
import com.cazadoxs.escanerlimpio.ui.screens.SearchScreen
import com.cazadoxs.escanerlimpio.ui.screens.SettingsScreen
import com.cazadoxs.escanerlimpio.ui.theme.EscanerTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

data class IncomingShare(val uris: List<Uri>, val isPdf: Boolean)

/** Folder that the next scan or import should land in. */
private class FolderTarget(var folderId: String? = null)

/** Entry points every screen can trigger. */
class AppActions(
    val scan: (folderId: String?) -> Unit,
    val pickPhotos: (folderId: String?) -> Unit,
    val pickPdf: (folderId: String?) -> Unit,
    val snackbar: (String) -> Unit,
    val nav: NavHostController,
)

val LocalActions = staticCompositionLocalOf<AppActions> { error("No actions") }

@Composable
fun app(): EscanerApp = LocalContext.current.applicationContext as EscanerApp

object Routes {
    const val HOME = "inicio"
    const val SEARCH = "buscar"
    const val SETTINGS = "ajustes"
    const val PRO = "pro"
    const val RENTA = "renta"
    const val ONBOARDING = "bienvenida"
    fun doc(id: String) = "doc/$id"
    fun pages(id: String) = "paginas/$id"
    fun category(c: Category) = "carpeta/c/${c.name}"
    fun folder(id: String) = "carpeta/f/$id"
}

@Composable
fun EscanerRoot(activity: MainActivity, locked: Boolean, onUnlocked: () -> Unit) {
    val app = app()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    EscanerTheme(settings.theme) {
        if (locked) {
            LockScreen(activity, onUnlocked)
            return@EscanerTheme
        }
        val nav = rememberNavController()
        val snackbarHost = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val isPro by app.billing.isPro.collectAsStateWithLifecycle()
        val target = remember { FolderTarget() }

        val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
            if (uris.isNotEmpty()) app.service.importImages(uris, target.folderId)
        }
        val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) app.service.importPdf(uri, target.folderId)
        }
        val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty()
            if (pages.isNotEmpty()) app.service.importImages(pages.map { it.imageUri }, target.folderId)
        }

        val actions = remember(nav) {
            AppActions(
                scan = { folder ->
                    target.folderId = folder
                    scope.launch {
                        val sender = Scanner.intentSender(activity)
                        LockGuard.expectExternalFlow()
                        if (sender != null) {
                            scanner.launch(IntentSenderRequest.Builder(sender).build())
                        } else {
                            snackbarHost.showSnackbar("Este móvil no tiene el escáner de Google. Elige las fotos del documento.")
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                },
                pickPhotos = { folder ->
                    target.folderId = folder
                    LockGuard.expectExternalFlow()
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                pickPdf = { folder ->
                    if (!app.billing.isPro.value) {
                        nav.navigate(Routes.PRO)
                    } else {
                        target.folderId = folder
                        LockGuard.expectExternalFlow()
                        pdfPicker.launch(arrayOf("application/pdf"))
                    }
                },
                snackbar = { msg -> scope.launch { snackbarHost.showSnackbar(msg) } },
                nav = nav,
            )
        }

        // Files shared from other apps.
        val incoming = activity.incoming
        LaunchedEffect(incoming, isPro) {
            val share = incoming ?: return@LaunchedEffect
            when {
                !share.isPdf -> app.service.importImages(share.uris)
                isPro -> share.uris.forEach { app.service.importPdf(it) }
                else -> {
                    nav.navigate(Routes.PRO)
                    snackbarHost.showSnackbar("Importar PDF de otras apps es parte de Pro.")
                }
            }
            activity.incoming = null
        }

        val work by app.service.work.collectAsStateWithLifecycle()
        LaunchedEffect(work) {
            when (val w = work) {
                is Work.Finished -> {
                    app.service.dismissWork()
                    if (w.docId.isNotEmpty()) {
                        val r = snackbarHost.showSnackbar(w.message, actionLabel = "Abrir", duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) nav.navigate(Routes.doc(w.docId))
                    } else {
                        snackbarHost.showSnackbar(w.message)
                    }
                }
                is Work.Failed -> {
                    app.service.dismissWork()
                    snackbarHost.showSnackbar(w.message, duration = SnackbarDuration.Long)
                }
                else -> Unit
            }
        }
        val billingMessage by app.billing.message.collectAsStateWithLifecycle()
        LaunchedEffect(billingMessage) {
            billingMessage?.let {
                app.billing.clearMessage()
                snackbarHost.showSnackbar(it)
            }
        }

        val start = if (settings.onboardingDone) Routes.HOME else Routes.ONBOARDING
        CompositionLocalProvider(LocalActions provides actions) {
            Box(Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = start) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onDone = {
                            app.settings.update { it.copy(onboardingDone = true) }
                            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        })
                    }
                    composable(Routes.HOME) { HomeScreen() }
                    composable(Routes.SEARCH) { SearchScreen() }
                    composable(Routes.SETTINGS) { SettingsScreen(activity) }
                    composable(Routes.PRO) { ProScreen(activity) }
                    composable(Routes.RENTA) { RentaScreen() }
                    composable("doc/{id}") { e -> DocumentScreen(e.arguments?.getString("id").orEmpty()) }
                    composable("paginas/{id}") { e -> PagesScreen(e.arguments?.getString("id").orEmpty()) }
                    composable("carpeta/{kind}/{key}") { e ->
                        CollectionScreen(e.arguments?.getString("kind").orEmpty(), e.arguments?.getString("key").orEmpty())
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
                    AnimatedVisibility(work is Work.Running, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                        (work as? Work.Running)?.let { WorkCard(it) }
                    }
                    SnackbarHost(snackbarHost)
                }
            }
        }
    }
}

@Composable
private fun WorkCard(w: Work.Running) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ScanningSheet()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Procesando en tu móvil", style = MaterialTheme.typography.titleSmall)
                Text(w.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                if (w.total > 1) {
                    LinearProgressIndicator(progress = { w.done.toFloat() / w.total }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
