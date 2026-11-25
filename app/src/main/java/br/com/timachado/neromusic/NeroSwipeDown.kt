package br.com.timachado.neromusic

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// Explicit drag target on the header, leaving normal vertical list scrolling untouched.
@Composable
internal fun Modifier.neroSwipeDown(onClose:()->Unit):Modifier{
    val close=rememberUpdatedState(onClose)
    return pointerInput(Unit){
        var distance=0f
        val threshold=72.dp.toPx()
        detectVerticalDragGestures(onDragStart={distance=0f},onDragCancel={distance=0f},
            onDragEnd={if(distance>=threshold)close.value();distance=0f},
            onVerticalDrag={change,amount->change.consume();distance=(distance+amount).coerceAtLeast(0f)})
    }
}
