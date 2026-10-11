package com.cazadoxs.escanerlimpio.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.ui.graphics.vector.ImageVector
import com.cazadoxs.escanerlimpio.classify.Category

val Category.icon: ImageVector
    get() = when (this) {
        Category.IDENTIDAD -> Icons.Outlined.Badge
        Category.NOMINAS -> Icons.Outlined.Payments
        Category.FACTURAS -> Icons.Outlined.ReceiptLong
        Category.TICKETS -> Icons.Outlined.ShoppingBag
        Category.HACIENDA -> Icons.Outlined.AccountBalance
        Category.BANCO -> Icons.Outlined.AccountBalanceWallet
        Category.CONTRATOS -> Icons.Outlined.Gavel
        Category.SEGUROS -> Icons.Outlined.HealthAndSafety
        Category.SALUD -> Icons.Outlined.LocalHospital
        Category.VEHICULO -> Icons.Outlined.DirectionsCar
        Category.ESTUDIOS -> Icons.Outlined.School
        Category.OTROS -> Icons.Outlined.Description
    }
