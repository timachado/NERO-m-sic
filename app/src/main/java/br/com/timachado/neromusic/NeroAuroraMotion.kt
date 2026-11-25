package br.com.timachado.neromusic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlin.math.sin
import kotlinx.coroutines.delay

// Decorative motion only; does not capture audio, use the microphone or change playback.
@Composable
internal fun NeroAuroraBackdrop(tone:Color,playing:Boolean,modifier:Modifier=Modifier){
    val phase=remember{Animatable(0f)}
    val owner=LocalLifecycleOwner.current
    var visible by remember(owner){mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(owner){
        val listener=LifecycleEventObserver{_,_->visible=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        owner.lifecycle.addObserver(listener)
        onDispose{owner.lifecycle.removeObserver(listener)}
    }
    LaunchedEffect(playing,visible){
        if(playing&&visible){
            while(true){phase.animateTo(1f,tween(4800));phase.animateTo(0f,tween(4800))}
        }
    }
    Canvas(modifier){
        val p=phase.value
        val radius=size.width*.82f
        drawRect(Brush.verticalGradient(listOf(tone,Color(0xFF071619),Color.Black)))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF00E5FF).copy(alpha=.15f),Color.Transparent),center=Offset(size.width*(.13f+.17f*p),size.height*.32f),radius=radius),radius=radius,center=Offset(size.width*(.13f+.17f*p),size.height*.32f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF39FF14).copy(alpha=.13f),Color.Transparent),center=Offset(size.width*(.9f-.14f*p),size.height*.53f),radius=radius),radius=radius,center=Offset(size.width*(.9f-.14f*p),size.height*.53f))
    }
}

@Composable
internal fun NeroMotionBars(playing:Boolean,modifier:Modifier=Modifier){
    var frame by remember{mutableIntStateOf(0)}
    val owner=LocalLifecycleOwner.current
    var visible by remember(owner){mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(owner){
        val listener=LifecycleEventObserver{_,_->visible=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        owner.lifecycle.addObserver(listener);onDispose{owner.lifecycle.removeObserver(listener)}
    }
    LaunchedEffect(playing,visible){if(playing&&visible)while(true){delay(110);frame=(frame+1)%600}}
    Canvas(modifier){
        val count=42
        val step=size.width/count
        for(i in 0 until count){
            val envelope=1f-kotlin.math.abs(i-(count-1)/2f)/(count/2f)
            val motion=if(playing&&visible)(.45f+.45f*sin(frame*.21+i*.71).toFloat())else .20f
            val height=(size.height*(.12f+envelope*motion*.78f)).coerceIn(1f,size.height)
            drawRoundRect(color=if(i<count/2)Color(0xFF39FF14).copy(alpha=.38f+envelope*.5f)else Color(0xFF00E5FF).copy(alpha=.26f+envelope*.45f),
                topLeft=Offset(i*step+step*.24f,size.height-height),size=Size(step*.48f,height),cornerRadius=CornerRadius(step*.24f))
        }
    }
}
