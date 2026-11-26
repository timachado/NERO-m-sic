package br.com.timachado.neromusic

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.Player

// Widget and playback service share the default app process and main looper.
// Never creates a second player or starts background playback from an empty session.
internal object NeroWidgetState {
    private var player:Player?=null
    private var last:WidgetSnapshot?=null
    private data class WidgetSnapshot(val title:String,val artist:String,val playing:Boolean,val previous:Boolean,val next:Boolean,val hasTrack:Boolean)
    private fun snapshot():WidgetSnapshot{
        val p=player
        val item=p?.currentMediaItem
        if(p==null||item==null)return WidgetSnapshot("Sua música","Abra o NERO",false,false,false,false)
        return WidgetSnapshot(
            p.mediaMetadata.title?.toString().orEmpty().ifBlank{item.mediaMetadata.title?.toString().orEmpty()}.ifBlank{"Faixa atual"},
            p.mediaMetadata.artist?.toString().orEmpty().ifBlank{item.mediaMetadata.artist?.toString().orEmpty()},
            neroPlayRequested(p.playWhenReady,p.playbackState),
            p.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)&&p.hasPreviousMediaItem(),
            p.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)&&p.hasNextMediaItem(),
            true
        )
    }
    fun attach(context:Context,p:Player){player=p;refresh(context,true)}
    fun detach(context:Context){player=null;refresh(context,true)}
    fun control(context:Context,action:String){
        val p=player?:return
        if(p.currentMediaItem==null)return
        when(action){
            NeroWidget.ACTION_TOGGLE->toggleNeroPlayback(p)
            NeroWidget.ACTION_PREVIOUS->if(p.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)&&p.hasPreviousMediaItem())p.seekToPreviousMediaItem()
            NeroWidget.ACTION_NEXT->if(p.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)&&p.hasNextMediaItem())p.seekToNextMediaItem()
        }
        refresh(context,true)
    }
    fun refresh(context:Context,force:Boolean=false){
        val manager=AppWidgetManager.getInstance(context)
        val ids=manager.getAppWidgetIds(ComponentName(context,NeroWidget::class.java))
        if(ids.isEmpty())return
        val state=snapshot()
        if(!force&&state==last)return
        val views=RemoteViews(context.packageName,R.layout.nero_widget)
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun control(action:String,code:Int)=PendingIntent.getBroadcast(context,code,Intent(context,NeroWidget::class.java).setAction(action),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setTextViewText(R.id.widget_title,state.title)
        views.setTextViewText(R.id.widget_artist,state.artist)
        views.setImageViewResource(R.id.widget_toggle,if(state.playing)R.drawable.nero_widget_pause else R.drawable.nero_widget_play)
        views.setContentDescription(R.id.widget_toggle,if(state.playing)"Pausar" else "Reproduzir")
        views.setInt(R.id.widget_previous,"setImageAlpha",if(state.previous)255 else 90)
        views.setInt(R.id.widget_next,"setImageAlpha",if(state.next)255 else 90)
        views.setOnClickPendingIntent(R.id.widget_open,open)
        views.setOnClickPendingIntent(R.id.widget_toggle,if(state.hasTrack)control(NeroWidget.ACTION_TOGGLE,1)else open)
        views.setOnClickPendingIntent(R.id.widget_previous,if(state.hasTrack)control(NeroWidget.ACTION_PREVIOUS,2)else open)
        views.setOnClickPendingIntent(R.id.widget_next,if(state.hasTrack)control(NeroWidget.ACTION_NEXT,3)else open)
        manager.updateAppWidget(ids,views)
        last=state
    }
}

class NeroWidget:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
        NeroWidgetState.refresh(context,true)
    }
    override fun onReceive(context:Context,intent:Intent){
        super.onReceive(context,intent)
        when(intent.action){
            ACTION_TOGGLE,ACTION_PREVIOUS,ACTION_NEXT->NeroWidgetState.control(context,intent.action.orEmpty())
        }
    }
    companion object{
        const val ACTION_TOGGLE="br.com.timachado.neromusic.WIDGET_TOGGLE"
        const val ACTION_PREVIOUS="br.com.timachado.neromusic.WIDGET_PREVIOUS"
        const val ACTION_NEXT="br.com.timachado.neromusic.WIDGET_NEXT"
    }
}
