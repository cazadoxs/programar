package com.cazadoxs.filtro400.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.cazadoxs.filtro400.ui.theme.Filtro400Theme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** ¿Es Filtro 400 la app de filtrado de llamadas del sistema? */
    val roleHeld = mutableStateOf(false)
    val roleAvailable = mutableStateOf(true)
    val notificationsGranted = mutableStateOf(true)
    val pendingDestination = mutableStateOf<String?>(null)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshSystemState()
        if (roleHeld.value) askNotificationsIfNeeded()
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshSystemState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingDestination.value = intent?.getStringExtra(EXTRA_DESTINATION)
        vm.connectBilling()
        setContent {
            Filtro400Theme {
                AppRoot(vm = vm, host = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_DESTINATION)?.let { pendingDestination.value = it }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemState()
    }

    private fun refreshSystemState() {
        val rm = getSystemService(RoleManager::class.java)
        roleAvailable.value = rm?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
        roleHeld.value = roleAvailable.value && rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        notificationsGranted.value = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestScreeningRole() {
        val rm = getSystemService(RoleManager::class.java) ?: return
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return
        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    fun askNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted.value) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
    }
}
