package br.com.timachado.neromusic

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Refresh entitlement after returning from the external Core checkout.
 * Lifecycle events never grant access: they only request a fresh server read.
 */
@Composable
internal fun rememberNeroCommerceResume():Int {
    val lifecycle=(LocalContext.current as? LifecycleOwner)?.lifecycle
    var signal by remember(lifecycle){mutableIntStateOf(0)}
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_RESUME)signal++
        }
        lifecycle?.addObserver(observer)
        onDispose{lifecycle?.removeObserver(observer)}
    }
    return signal
}
