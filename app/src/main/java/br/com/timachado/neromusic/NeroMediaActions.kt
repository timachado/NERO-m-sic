package br.com.timachado.neromusic

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

@UnstableApi
internal object NeroMediaActions {
    val favorite=SessionCommand("nero.media.favorite",Bundle.EMPTY)
    val repeat=SessionCommand("nero.media.repeat",Bundle.EMPTY)
    fun reference(item:MediaItem?):OnlineRef?=item?.let{openRefFromItem(it)?:youtubeRefFromItem(it)}
    fun localId(item:MediaItem?):String?=item?.mediaId?.takeIf{it.toLongOrNull()!=null}
    fun canFavorite(item:MediaItem?)=reference(item)!=null||localId(item)!=null
    fun liked(context:Context,item:MediaItem?):Boolean{
        val ref=reference(item)
        if(ref!=null)return runCatching{readOnlineFavorites(context).any{it.id==ref.id}}.getOrDefault(false)
        val id=localId(item)?:return false
        return id in context.getSharedPreferences("nero_library",0).getStringSet("favorites",emptySet()).orEmpty()
    }
    fun toggle(context:Context,item:MediaItem?):Boolean=runCatching{
        val ref=reference(item)
        if(ref!=null){
            val current=readOnlineFavorites(context)
            saveOnlineFavorites(context,if(current.any{it.id==ref.id})current.filterNot{it.id==ref.id}else listOf(ref)+current)
        }else{
            val id=localId(item)?:return false
            val prefs=context.getSharedPreferences("nero_library",0)
            val current=prefs.getStringSet("favorites",emptySet()).orEmpty().toSet()
            prefs.edit().putStringSet("favorites",if(id in current)current-id else current+id).apply()
        }
        true
    }.getOrDefault(false)
    fun layout(context:Context,player:Player):List<CommandButton>{
        val item=player.currentMediaItem
        if(item==null)return emptyList()
        val liked=liked(context,item)
        val buttons=mutableListOf<CommandButton>()
        if(canFavorite(item))buttons+=CommandButton.Builder(if(liked)CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if(liked)"Remover dos favoritos" else "Curtir música").setSessionCommand(favorite).build()
        if(!NeroPlaybackAccess.allowed())return buttons
        val mode=player.repeatMode
        buttons+=CommandButton.Builder(when(mode){Player.REPEAT_MODE_ONE->CommandButton.ICON_REPEAT_ONE;Player.REPEAT_MODE_ALL->CommandButton.ICON_REPEAT_ALL;else->CommandButton.ICON_REPEAT_OFF})
            .setDisplayName(when(mode){Player.REPEAT_MODE_ONE->"Repetir faixa";Player.REPEAT_MODE_ALL->"Repetir fila";else->"Repetição desligada"})
            .setSessionCommand(repeat).build()
        return buttons
    }
}
