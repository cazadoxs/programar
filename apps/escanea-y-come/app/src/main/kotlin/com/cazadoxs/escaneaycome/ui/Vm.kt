package com.cazadoxs.escaneaycome.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cazadoxs.escaneaycome.AppContainer
import com.cazadoxs.escaneaycome.EscaneaApp

@Composable
fun container(): AppContainer = (LocalContext.current.applicationContext as EscaneaApp).container

/** viewModel() with access to the app container, keyed so each argument set gets its own instance. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = container()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}
