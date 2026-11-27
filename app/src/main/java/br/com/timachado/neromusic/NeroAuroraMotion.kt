package br.com.timachado.neromusic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlin.math.sin
import kotlinx.coroutines.delay

// Decorative motion only; does not capture audio, use the microphone or change playback.
@Composable
internal fun NeroAuroraBackdrop(tone:Color,playing:Boolean,modifier:Modifier=Modifier,primary:Color=MaterialTheme.colorScheme.primary,secondary:Color=MaterialTheme.colorScheme.secondary){
    val background=MaterialTheme.colorScheme.background
    val panel=MaterialTheme.colorScheme.surfaceVariant
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
        drawRect(Brush.verticalGradient(listOf(tone,panel,background)))
        drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha=.15f),Color.Transparent),center=Offset(size.width*(.13f+.17f*p),size.height*.32f),radius=radius),radius=radius,center=Offset(size.width*(.13f+.17f*p),size.height*.32f))
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha=.21f),Color.Transparent),center=Offset(size.width*(.9f-.14f*p),size.height*.53f),radius=radius),radius=radius,center=Offset(size.width*(.9f-.14f*p),size.height*.53f))
        val w=size.width;val h=size.height
        for(i in 0..1){
            val y=h*(if(i==0).27f else .88f);val drift=(p-.5f)*h*.035f
            val path=Path().apply{moveTo(-w*.12f,y+h*.13f);cubicTo(w*.22f,y-h*.14f+drift,w*.59f,y+h*.16f-drift,w*1.12f,y-h*.09f)}
            val brush=Brush.horizontalGradient(listOf(Color.Transparent,if(i==0)secondary else primary,Color.Transparent))
            for(j in 5 downTo 1){drawPath(path,brush,alpha=.025f,style=Stroke(width=j*10.dp.toPx()))}
            drawPath(path,brush,alpha=.24f,style=Stroke(width=2.dp.toPx()))
        }
    }
}

@Composable
internal fun NeroMotionBars(playing:Boolean,modifier:Modifier=Modifier,primary:Color=MaterialTheme.colorScheme.primary,secondary:Color=MaterialTheme.colorScheme.secondary){
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
            drawRoundRect(color=if(i<count/2)primary.copy(alpha=.38f+envelope*.5f)else secondary.copy(alpha=.26f+envelope*.45f),
                topLeft=Offset(i*step+step*.24f,size.height-height),size=Size(step*.48f,height),cornerRadius=CornerRadius(step*.24f))
        }
    }
}
