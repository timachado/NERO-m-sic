package br.com.timachado.neromusic

import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException

// Curated discovery, not a claim that the upstream source exposes verified genre metadata.
internal data class NeroGenre(val label:String,val artists:List<String>)
internal fun genreKey(value:String):String =
    Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        .lowercase(Locale.ROOT).replace("&"," e ").replace(Regex("[^a-z0-9]+")," ").trim()
private fun artistGenreKey(value:String)=genreKey(value).removeSuffix(" topic").removeSuffix(" oficial").removeSuffix(" official").trim()
internal val neroGenres=listOf(
    NeroGenre("Pagode",listOf("Grupo Menos É Mais","Turma do Pagode","Sorriso Maroto","Dilsinho","Ferrugem","Péricles","Belo","Pixote","Exaltasamba","Thiaguinho","Mumuzinho","Revelação","Grupo Revelação","Jeito Moleque","Raça Negra","Só Pra Contrariar","Suel")),
    NeroGenre("Sertanejo",listOf("Zé Neto e Cristiano","Jorge e Mateus","Henrique e Juliano","Clayton e Romário","Hugo e Guilherme","Marília Mendonça","Maiara e Maraisa","Gusttavo Lima","Luan Santana","Matheus e Kauan","Ana Castela","Simone Mendes","Zezé Di Camargo e Luciano","Chitãozinho e Xororó")),
    NeroGenre("Pop Brasil",listOf("Anitta","Ludmilla","Pabllo Vittar","Luísa Sonza","Jão","IZA","Gloria Groove","Melim")),
    NeroGenre("Gospel",listOf("Fernandinho","Gabriela Rocha","Aline Barros","Diante do Trono","Isaias Saad","Anderson Freire","Morada","Bruna Karla","Cassiane")),
    NeroGenre("Forró",listOf("Wesley Safadão","Xand Avião","Nattan","Zé Vaqueiro","Tarcísio do Acordeon","João Gomes","Aviões do Forró","Calcinha Preta","Limão com Mel")),
    NeroGenre("Samba",listOf("Zeca Pagodinho","Martinho da Vila","Beth Carvalho","Alcione","Cartola","Jorge Aragão","Paulinho da Viola","Fundo de Quintal")),
    NeroGenre("Rock",listOf("Queen","AC/DC","Legião Urbana","Nirvana","Foo Fighters","The Beatles","Iron Maiden","Guns N' Roses")),
    NeroGenre("MPB",listOf("Marisa Monte","Caetano Veloso","Gilberto Gil","Djavan","Gal Costa","Elis Regina","Milton Nascimento","Chico Buarque")),
    NeroGenre("Eletrônica",listOf("Alok","David Guetta","Calvin Harris","Avicii","Martin Garrix","Tiësto","Armin van Buuren")),
    NeroGenre("Hip-hop",listOf("Emicida","Racionais MC's","Criolo","Sabotage","Djonga","Rashid","BK'")),
    NeroGenre("Jazz",listOf("Miles Davis","John Coltrane","Ella Fitzgerald","Louis Armstrong","Bill Evans","Thelonious Monk")),
    NeroGenre("Clássica",listOf("Yo-Yo Ma","Lang Lang","Hilary Hahn","Martha Argerich","Itzhak Perlman","André Rieu"))
)
internal fun genreForQuery(query:String):NeroGenre?{
    val key=genreKey(query).removeSuffix(" novidades").trim()
    return neroGenres.firstOrNull{genreKey(it.label)==key || (it.label=="Pop Brasil"&&key=="pop")}
}
internal fun NeroGenre.matchesArtist(artist:String):Boolean{
    val normalized=artistGenreKey(artist)
    return normalized.isNotBlank()&&artists.any{artistGenreKey(it)==normalized}
}
internal fun genreTracks(genre:NeroGenre,rows:List<NativeMusicHit>):List<NativeMusicHit> =
    rows.filter{genre.matchesArtist(it.artist)}.distinctBy{it.id}
internal fun genreCollections(genre:NeroGenre,rows:List<NativeCollection>,kind:NativeCollectionKind):List<NativeCollection> =
    rows.filter{it.kind==kind&&genre.matchesArtist(if(kind==NativeCollectionKind.ARTIST)it.title else it.credit)}.distinctBy{it.url}
internal suspend fun discoverGenreTracks(genre:NeroGenre,clips:Boolean):List<NativeMusicHit>{
    val rows=mutableListOf<NativeMusicHit>()
    var lastError:Exception?=null
    var completed=0
    for(seed in genre.artists.take(4)){
        try{
            val found=if(clips)NativeMusicCatalog.searchVideos(seed)else NativeMusicCatalog.search(seed)
            rows+=genreTracks(genre,found).take(8)
            completed++
        }catch(e:CancellationException){throw e}catch(e:Exception){lastError=e}
    }
    if(completed==0&&lastError!=null)throw lastError
    return genreTracks(genre,rows)
}
internal suspend fun discoverGenreCollections(genre:NeroGenre,kind:NativeCollectionKind):List<NativeCollection>{
    val rows=mutableListOf<NativeCollection>()
    for(seed in genre.artists.take(3)){
        try{rows+=genreCollections(genre,NativeMusicCatalog.collections(seed,kind),kind).take(3)}
        catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    return genreCollections(genre,rows,kind)
}
