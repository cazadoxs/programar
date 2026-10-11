package com.cazadoxs.descifra

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cazadoxs.descifra.core.model.Deadline
import com.cazadoxs.descifra.ui.BigButton
import com.cazadoxs.descifra.ui.DescifraTheme
import com.cazadoxs.descifra.ui.DetailActions
import com.cazadoxs.descifra.ui.DetailScreen
import com.cazadoxs.descifra.ui.HomeScreen
import com.cazadoxs.descifra.ui.LockScreen
import com.cazadoxs.descifra.ui.OnboardingScreen
import com.cazadoxs.descifra.ui.PaywallScreen
import com.cazadoxs.descifra.ui.PrivacyScreen
import com.cazadoxs.descifra.ui.ReviewScreen
import com.cazadoxs.descifra.ui.SettingsActions
import com.cazadoxs.descifra.ui.SettingsScreen
import com.cazadoxs.descifra.ui.palette
import com.cazadoxs.descifra.util.Export
import com.cazadoxs.descifra.work.Reminders
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Letters are sensitive: block screenshots and hide the recents thumbnail unless allowed.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                vm.settings.collect { s ->
                    if (s.allowScreenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent { DescifraTheme { Root() } }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { (application as DescifraApp).billing.refresh() }
        vm.refreshNano()
    }

    override fun onStop() {
        super.onStop()
        vm.lockNow()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("letter")?.let { id ->
            if (vm.repo.get(id) != null && vm.ui.value.screen != Screen.Detail(id)) vm.resetTo(Screen.Detail(id))
            intent.removeExtra("letter")
        }
        if (intent.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        @Suppress("DEPRECATION")
        val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        when {
            type == "text/plain" -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.onText(it) }
            type == "application/pdf" && stream != null -> vm.onPdf(stream)
            type.startsWith("image/") && stream != null -> vm.onImage(stream)
        }
        // Consume it so a rotation does not re-import the same letter.
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    // --- biometrics ------------------------------------------------------------------------
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private fun canAuthenticate(): Boolean = BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(onSuccess: () -> Unit, onFail: (() -> Unit)? = null) {
        if (!canAuthenticate()) {
            // No screen lock on the phone: nothing to protect with, so do not trap the user out.
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFail?.invoke() }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear Descifra")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    // --- UI --------------------------------------------------------------------------------
    @Composable
    private fun Root() {
        val p = MaterialTheme.palette
        val ui by vm.ui.collectAsState()
        val letters by vm.letters.collectAsState()
        val settings by vm.settings.collectAsState()
        val pro by vm.pro.collectAsState()

        val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uris = result?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) vm.onScanned(uris)
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val type = contentResolver.getType(uri).orEmpty()
                if (type == "application/pdf") vm.onPdf(uri) else vm.onImage(uri)
            }
        }
        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshNano() }
        var canNotify by remember { mutableStateOf(Reminders.canNotify(this)) }

        val snackbar = ui.message
        LaunchedEffect(ui.stack.size, ui.busy) { canNotify = Reminders.canNotify(this@MainActivity) }
        LaunchedEffect(settings.reminderHour) { Reminders.schedule(this@MainActivity, settings.reminderHour) }

        // Ask for notification permission once, in context: after the first letter is saved.
        LaunchedEffect(letters.size) {
            val prefs = getSharedPreferences("ui", MODE_PRIVATE)
            if (letters.isNotEmpty() && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this@MainActivity) && !prefs.getBoolean("askedNotif", false)) {
                prefs.edit().putBoolean("askedNotif", true).apply()
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val startScan = {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(5)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(this@MainActivity)
                .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { vm.toast("No se pudo abrir el escáner. Prueba con «Foto o PDF» o pega el texto.") }
            Unit
        }

        BackHandler(enabled = ui.stack.size > 1 || ui.busy != null) { vm.back() }

        Box(Modifier.fillMaxSize().background(p.paper)) {
            if (ui.locked && settings.onboarded) {
                LaunchedEffect(Unit) { authenticate(onSuccess = vm::unlock) }
                LockScreen(onUnlock = { authenticate(onSuccess = vm::unlock) })
            } else when (val screen = ui.screen) {
                Screen.Onboarding -> OnboardingScreen(onDone = vm::finishOnboarding)
                Screen.Home -> HomeScreen(
                    letters = letters,
                    onScan = startScan,
                    onPaste = {
                        val clip = getSystemService(ClipboardManager::class.java).primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
                        vm.onText(text)
                    },
                    onImport = { picker.launch(arrayOf("image/*", "application/pdf")) },
                    onOpen = { vm.go(Screen.Detail(it)) },
                    onSettings = { vm.go(Screen.Settings) },
                )
                Screen.Review -> ReviewScreen(
                    draft = ui.draft,
                    onEdit = vm::editDraft,
                    onAnalyze = vm::analyzeDraft,
                    onBack = { vm.back() },
                    cloudHint = cloudHint(settings, pro.isPro),
                )
                is Screen.Detail -> {
                    val letter = letters.firstOrNull { it.id == screen.id }
                    if (letter == null) LaunchedEffect(Unit) { vm.back() } else DetailScreen(letter, pro.isPro, ui.busy != null, detailActions(letter.id))
                }
                Screen.Settings -> SettingsScreen(
                    s = settings,
                    isPro = pro.isPro,
                    nano = ui.nano,
                    nanoDownloading = ui.nanoDownloading,
                    canNotify = canNotify,
                    version = BuildConfig.VERSION_NAME,
                    a = SettingsActions(
                        onBack = { vm.back() },
                        update = vm::update,
                        saveKey = { provider, key -> (application as DescifraApp).repository.setApiKey(provider, key) },
                        openUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        downloadNano = vm::downloadNano,
                        onPaywall = { vm.go(Screen.Paywall) },
                        onPrivacy = { vm.go(Screen.Privacy) },
                        onErase = vm::eraseEverything,
                        onNotificationsPermission = { if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onLockToggled = { on ->
                            if (on) authenticate(onSuccess = { vm.update { it.copy(lockWithBiometrics = true) } })
                            else vm.update { it.copy(lockWithBiometrics = false) }
                        },
                    ),
                )
                Screen.Paywall -> PaywallScreen(pro, onBuy = { lifecycleScope.launch { (application as DescifraApp).billing.buy(this@MainActivity) } }, onBack = { vm.back() })
                Screen.Privacy -> PrivacyScreen(onBack = { vm.back() })
            }

            if (ui.busy != null) BusyOverlay(ui.busy!!)
            val msg = snackbar ?: pro.message
            if (msg != null) Toast(msg) { vm.toast(null); (application as DescifraApp).billing.clearMessage() }
        }
    }

    private fun cloudHint(s: com.cazadoxs.descifra.data.Settings, isPro: Boolean): String? = when {
        s.mode == com.cazadoxs.descifra.data.AnalysisMode.RULES -> "Se analizará solo con reglas, sin IA."
        isPro && s.hasKey && s.cloudConsent && s.mode != com.cazadoxs.descifra.data.AnalysisMode.ON_DEVICE ->
            "Se usará " + s.provider.label + " con tu clave" + if (s.redact) " (datos personales ocultos)." else "."
        else -> "El análisis se hace en tu móvil."
    }

    private fun detailActions(id: String) = DetailActions(
        onBack = { vm.back() },
        onToggleDone = { vm.toggleDone(id) },
        onDelete = { vm.delete(id) },
        onNote = { vm.setNote(id, it) },
        onCalendar = { d: Deadline ->
            val l = vm.repo.get(id) ?: return@DetailActions
            try {
                startActivity(Reminders.addToCalendarIntent(l.analysis.title, d))
            } catch (e: Exception) {
                vm.toast("No hay ninguna app de calendario instalada.")
            }
        },
        onCopy = { copy(it, "Copiado") },
        onShare = {
            val l = vm.repo.get(id) ?: return@DetailActions
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Export.summaryText(l)), "Compartir resumen"))
        },
        onExportPdf = {
            val l = vm.repo.get(id) ?: return@DetailActions
            lifecycleScope.launch {
                try {
                    val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Export.pdf(this@MainActivity, l) }
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            "Exportar PDF",
                        ),
                    )
                } catch (e: Exception) {
                    vm.toast("No se pudo crear el PDF.")
                }
            }
        },
        onAsk = { vm.ask(id, it) },
        onDraft = { vm.makeDraft(id, it) },
        onPaywall = { vm.go(Screen.Paywall) },
        onToggleReminders = {
            vm.toggleReminders(id)
            if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this)) notifRequest()
        },
        onChangeReceived = { vm.changeReceived(id, it) },
        onCopyDraft = { vm.repo.get(id)?.draft?.let { copy(it, "Borrador copiado") } },
    )

    private fun notifRequest() {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    /** Copies text and marks it sensitive so Android hides it from the clipboard preview. */
    private fun copy(text: String, toast: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("Descifra", text)
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= 33) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            else putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        cm.setPrimaryClip(clip)
        vm.toast(toast)
    }
}

@Composable
private fun BusyOverlay(text: String) {
    val p = MaterialTheme.palette
    Box(Modifier.fillMaxSize().background(p.paper.copy(alpha = 0.92f)).clickable(enabled = true) { }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = p.stamp)
            Spacer(Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
            Text("Tarda unos segundos", color = p.inkSoft, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Toast(text: String, onDismiss: () -> Unit) {
    val p = MaterialTheme.palette
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text,
            color = p.paper,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .background(p.ink, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .clickable(onClick = onDismiss)
                .padding(16.dp),
        )
    }
}
