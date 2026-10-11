package io.github.cazadoxs.toma.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.backup.BackupManager
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.core.BackupCrypto
import io.github.cazadoxs.toma.core.BackupException
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.SectionTitle
import io.github.cazadoxs.toma.ui.components.SwitchRow
import io.github.cazadoxs.toma.ui.components.TomaTopBar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class Mode { EXPORT, IMPORT, AUTO }

@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit, onPlus: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsState()
    var mode by remember { mutableStateOf<Mode?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri ->
        if (uri != null) { pendingUri = uri; mode = Mode.EXPORT }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; mode = Mode.IMPORT }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.update { it.copy(autoBackupFolder = uri.toString()) }
            mode = Mode.AUTO
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TomaTopBar(stringResource(R.string.bk_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.bk_intro), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { exportLauncher.launch(BackupManager.suggestedName()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.bk_export))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.bk_import))
            }
            Text(stringResource(R.string.bk_restore_warn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(it), style = MaterialTheme.typography.titleSmall, color = if (it == R.string.bk_done_export || it == R.string.bk_done_import) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }

            SectionTitle(stringResource(R.string.bk_auto))
            LabelCard(accent = MaterialTheme.colorScheme.secondary) {
                if (!prefs.plusOwned) {
                    Text(stringResource(R.string.plus_locked), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.bk_auto_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onPlus) { Text(stringResource(R.string.settings_plus)) }
                } else {
                    SwitchRow(stringResource(R.string.bk_auto), stringResource(R.string.bk_auto_sub), prefs.autoBackupEnabled) { on ->
                        if (on) folderLauncher.launch(null) else {
                            vm.update { it.copy(autoBackupEnabled = false) }
                            BackupManager.scheduleAuto(context, false)
                            context.container.secrets.put(BackupManager.SECRET_AUTO_PASSWORD, null)
                        }
                    }
                    if (prefs.autoBackupEnabled) {
                        Text(
                            if (prefs.lastAutoBackupAt == 0L) stringResource(R.string.bk_auto_never)
                            else stringResource(R.string.bk_auto_last, DateFormat.getDateTimeInstance().format(Date(prefs.lastAutoBackupAt))),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    mode?.let { m ->
        PasswordDialog(
            confirm = m != Mode.IMPORT,
            onDismiss = { mode = null; pendingUri = null },
            onSubmit = { password ->
                val uri = pendingUri
                mode = null
                scope.launch {
                    message = try {
                        when (m) {
                            Mode.EXPORT -> { BackupManager.export(context, uri!!, password); R.string.bk_done_export }
                            Mode.IMPORT -> { BackupManager.import(context, uri!!, password); R.string.bk_done_import }
                            Mode.AUTO -> {
                                context.container.secrets.put(BackupManager.SECRET_AUTO_PASSWORD, password)
                                vm.update { it.copy(autoBackupEnabled = true) }
                                BackupManager.scheduleAuto(context, true)
                                BackupManager.runAuto(context)
                                R.string.bk_done_export
                            }
                        }
                    } catch (e: BackupException) {
                        when (e.reason) {
                            BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED -> R.string.bk_wrong
                            BackupException.Reason.NOT_A_BACKUP -> R.string.bk_not_backup
                            else -> R.string.bk_invalid
                        }
                    } catch (e: Exception) {
                        R.string.bk_error
                    } finally {
                        password.fill(' ')
                        pendingUri = null
                    }
                }
            },
        )
    }
}

@Composable
private fun PasswordDialog(confirm: Boolean, onDismiss: () -> Unit, onSubmit: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val tooShort = confirm && pw.length < BackupCrypto.MIN_PASSWORD_LENGTH
    val mismatch = confirm && pw != pw2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bk_password)) },
        text = {
            Column {
                OutlinedTextField(
                    pw, { pw = it.take(128) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), label = { Text(stringResource(R.string.bk_password)) },
                    isError = confirm && pw.isNotEmpty() && tooShort,
                    supportingText = { if (confirm && pw.isNotEmpty() && tooShort) Text(stringResource(R.string.bk_password_short, BackupCrypto.MIN_PASSWORD_LENGTH)) },
                )
                if (confirm) {
                    OutlinedTextField(
                        pw2, { pw2 = it.take(128) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), label = { Text(stringResource(R.string.bk_password_confirm)) },
                        isError = pw2.isNotEmpty() && mismatch,
                        supportingText = { if (pw2.isNotEmpty() && mismatch) Text(stringResource(R.string.bk_password_mismatch)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty() && !tooShort && !mismatch, onClick = { onSubmit(pw.toCharArray()) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
