package app.facil.launcher.system

import android.content.Context

/** getSystemService tipado y no nulo (en dispositivos reales estos servicios siempre existen). */
inline fun <reified T : Any> Context.svc(): T = checkNotNull(getSystemService(T::class.java)) { "servicio no disponible" }
