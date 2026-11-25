package br.com.timachado.neromusic
import androidx.media3.common.MediaItem
internal object NativeMusicCatalog{
    val enabled=false
    suspend fun search(query:String):List<NativeMusicHit> = emptyList()
    suspend fun resolve(hit:NativeMusicHit):MediaItem = error("Fonte não disponível neste build")
}
