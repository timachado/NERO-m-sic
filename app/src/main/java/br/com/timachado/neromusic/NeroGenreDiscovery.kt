package br.com.timachado.neromusic

import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException

// Curated discovery, not a claim that the upstream source exposes verified genre metadata.
internal data class NeroGenre(val label:String,val artists:List<String>,val releaseTag:String=genreKey(label),val aliases:List<String> = emptyList())
internal fun genreKey(value:String):String =
    Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        .lowercase(Locale.ROOT).replace("&"," e ").replace(Regex("[^a-z0-9]+")," ").trim()
private fun artistGenreKey(value:String)=genreKey(value).removeSuffix(" topic").removeSuffix(" oficial").removeSuffix(" official").trim()
internal val neroGenres=listOf(
    NeroGenre("Pagode",listOf("Grupo Menos É Mais","Turma do Pagode","Sorriso Maroto","Dilsinho","Ferrugem","Péricles","Belo","Pixote","Exaltasamba","Thiaguinho","Mumuzinho","Revelação","Grupo Revelação","Jeito Moleque","Raça Negra","Só Pra Contrariar","Suel")),
    NeroGenre("Sertanejo",listOf("Zé Neto e Cristiano","Jorge e Mateus","Henrique e Juliano","Clayton e Romário","Hugo e Guilherme","Marília Mendonça","Maiara e Maraisa","Gusttavo Lima","Luan Santana","Matheus e Kauan","Ana Castela","Simone Mendes","Zezé Di Camargo e Luciano","Chitãozinho e Xororó")),
    NeroGenre("Pop Brasil",listOf("Anitta","Ludmilla","Pabllo Vittar","Luísa Sonza","Jão","IZA","Gloria Groove","Melim"),"pop",listOf("pop","pop nacional","pop brasileiro")),
    NeroGenre("Gospel",listOf("Fernandinho","Gabriela Rocha","Aline Barros","Diante do Trono","Isaias Saad","Anderson Freire","Morada","Bruna Karla","Cassiane")),
    NeroGenre("Forró",listOf("Wesley Safadão","Xand Avião","Nattan","Zé Vaqueiro","Tarcísio do Acordeon","João Gomes","Aviões do Forró","Calcinha Preta","Limão com Mel")),
    NeroGenre("Samba",listOf("Zeca Pagodinho","Martinho da Vila","Beth Carvalho","Alcione","Cartola","Jorge Aragão","Paulinho da Viola","Fundo de Quintal")),
    NeroGenre("Rock",listOf("Queen","AC/DC","Legião Urbana","Nirvana","Foo Fighters","The Beatles","Iron Maiden","Guns N' Roses")),
    NeroGenre("MPB",listOf("Marisa Monte","Caetano Veloso","Gilberto Gil","Djavan","Gal Costa","Elis Regina","Milton Nascimento","Chico Buarque")),
    NeroGenre("Eletrônica",listOf("Alok","David Guetta","Calvin Harris","Avicii","Martin Garrix","Tiësto","Armin van Buuren"),"electronic",listOf("edm","música eletrônica")),
    NeroGenre("Hip-hop",listOf("Emicida","Racionais MC's","Criolo","Sabotage","Djonga","Rashid","BK'"),"hip hop",listOf("rap","rap nacional","rap brasileiro","hip hop nacional")),
    NeroGenre("Jazz",listOf("Miles Davis","John Coltrane","Ella Fitzgerald","Louis Armstrong","Bill Evans","Thelonious Monk")),
    NeroGenre("Clássica",listOf("Yo-Yo Ma","Lang Lang","Hilary Hahn","Martha Argerich","Itzhak Perlman","André Rieu"),"classical",listOf("música clássica","erudita")),
    NeroGenre("Funk",listOf("MC Tuto","MC Hariel","MC Ryan SP","MC IG","MC Cabelinho","MC Livinho","MC Don Juan","MC Fioti","MC Kevin","MC Davi"),"funk carioca",listOf("funk brasileiro","funk br","funk paulista","funk carioca")),
    NeroGenre("Trap",listOf("Matuê","Teto","WIU","Veigh","Orochi","Filipe Ret","KayBlack","Recayd Mob"),"trap",listOf("trap brasileiro")),
    NeroGenre("Rap internacional",listOf("Eminem","Nas","Tupac","Jay-Z","Kendrick Lamar","Drake","J. Cole"),"hip hop",listOf("rap internacional","hip hop internacional")),
    NeroGenre("Pop internacional",listOf("Taylor Swift","Ariana Grande","Bruno Mars","Dua Lipa","Lady Gaga","Ed Sheeran","Katy Perry","Michael Jackson"),"pop",listOf("pop mundial")),
    NeroGenre("Reggae",listOf("Bob Marley","Peter Tosh","Jimmy Cliff","Natiruts","Cidade Negra","Ponto de Equilíbrio"),"reggae",listOf()),
    NeroGenre("Reggaeton",listOf("Daddy Yankee","Don Omar","Bad Bunny","Karol G","J Balvin","Ozuna"),"reggaeton",listOf("reguetón")),
    NeroGenre("Piseiro",listOf("João Gomes","Zé Vaqueiro","Tarcísio do Acordeon","Os Barões da Pisadinha","Vitor Fernandes","Nattan"),"piseiro",listOf("pisadinha")),
    NeroGenre("Arrocha",listOf("Pablo","Tayrone","Silvanno Salles","Devinho Novaes","Toque Dez","Asas Livres"),"arrocha",listOf()),
    NeroGenre("Brega",listOf("Reginaldo Rossi","Amado Batista","Wanderley Andrade","Banda Calypso","Falcão"),"brega",listOf()),
    NeroGenre("Brega funk",listOf("MC Loma e as Gêmeas Lacração","Dadá Boladão","Shevchenko e Elloco","MC Troia"),"brega funk",listOf("bregafunk")),
    NeroGenre("Axé",listOf("Ivete Sangalo","Daniela Mercury","Chiclete com Banana","Banda Eva","Durval Lelys","Claudia Leitte"),"axe",listOf("axé music")),
    NeroGenre("Sertanejo raiz",listOf("Tião Carreiro e Pardinho","Tonico e Tinoco","Milionário e José Rico","Lourenço e Lourival","Cascatinha e Inhana"),"sertanejo",listOf("moda de viola","música caipira")),
    NeroGenre("Rock nacional",listOf("Legião Urbana","Capital Inicial","Titãs","Paralamas do Sucesso","Charlie Brown Jr.","Pitty","Skank"),"brazilian rock",listOf("rock brasileiro")),
    NeroGenre("Metal",listOf("Metallica","Iron Maiden","Black Sabbath","Megadeth","Sepultura","Slayer","Judas Priest"),"metal",listOf("heavy metal")),
    NeroGenre("Punk",listOf("Ramones","Sex Pistols","The Clash","Bad Religion","Dead Kennedys"),"punk rock",listOf("punk rock")),
    NeroGenre("Indie",listOf("Arctic Monkeys","The Strokes","Tame Impala","The xx","Vampire Weekend","Florence + The Machine"),"indie",listOf("indie rock","indie pop")),
    NeroGenre("R&B",listOf("Usher","Alicia Keys","Beyoncé","SZA","H.E.R.","Frank Ocean","The Weeknd"),"r&b",listOf("rnb","rhythm and blues")),
    NeroGenre("Soul",listOf("Aretha Franklin","Marvin Gaye","Otis Redding","Sam Cooke","Stevie Wonder","Tim Maia"),"soul",listOf()),
    NeroGenre("Blues",listOf("B.B. King","Muddy Waters","Robert Johnson","Buddy Guy","Etta James"),"blues",listOf()),
    NeroGenre("Country",listOf("Johnny Cash","Dolly Parton","Willie Nelson","Shania Twain","Luke Combs","Chris Stapleton"),"country",listOf()),
    NeroGenre("K-pop",listOf("BTS","BLACKPINK","TWICE","Stray Kids","EXO","SEVENTEEN"),"k-pop",listOf("kpop","pop coreano")),
    NeroGenre("J-pop",listOf("Hikaru Utada","YOASOBI","Ado","Kenshi Yonezu","Perfume"),"j-pop",listOf("jpop","pop japonês")),
    NeroGenre("Bossa nova",listOf("João Gilberto","Tom Jobim","Astrud Gilberto","Nara Leão","Vinicius de Moraes"),"bossa nova",listOf()),
    NeroGenre("Lo-fi",listOf("Jinsang","idealism","Nujabes","Kupla","eevee"),"lo-fi hip hop",listOf("lofi","lo fi")),
    NeroGenre("House",listOf("Frankie Knuckles","Daft Punk","Disclosure","Fisher","Duke Dumont"),"house",listOf("house music")),
    NeroGenre("Techno",listOf("Carl Cox","Jeff Mills","Charlotte de Witte","Amelie Lens","Richie Hawtin"),"techno",listOf()),
    NeroGenre("Trance",listOf("Armin van Buuren","Above & Beyond","Paul van Dyk","Ferry Corsten","Aly & Fila"),"trance",listOf()),
    NeroGenre("Disco",listOf("Donna Summer","Bee Gees","Chic","Gloria Gaynor","Village People"),"disco",listOf("disco music")),
    NeroGenre("Salsa",listOf("Celia Cruz","Marc Anthony","Héctor Lavoe","Willie Colón","Rubén Blades"),"salsa",listOf()),
    NeroGenre("Bachata",listOf("Romeo Santos","Aventura","Prince Royce","Juan Luis Guerra","Frank Reyes"),"bachata",listOf()),
    NeroGenre("Afrobeats",listOf("Burna Boy","Wizkid","Davido","Rema","Tems","Ayra Starr"),"afrobeats",listOf("afro beats")),
    NeroGenre("Louvor e adoração",listOf("Diante do Trono","Morada","Gabriela Rocha","Isaias Saad","Hillsong Worship","Elevation Worship","Bethel Music"),"worship",listOf("worship","louvor","adoração"))
)
internal fun NeroGenre.searchKeys()=(listOf(label)+aliases).map(::genreKey).filter{it.isNotBlank()}.distinct()
internal val neroGenreLabels:List<String> = listOf("Sertanejo","Pagode","Gospel","MPB","Pop","Rock","Samba","Forró","Eletrônica","Jazz","Hip-hop","Clássica")+neroGenres.drop(12).map{it.label}
internal val neroReleaseGenreOptions:List<Pair<String,String?>> = listOf("Todos" to null)+neroGenreLabels.mapNotNull{label->genreForQuery(label)?.let{label to it.releaseTag}}
internal fun genreForQuery(query:String):NeroGenre?{
    val key=genreKey(query).removeSuffix(" novidades").trim()
    return neroGenres.firstOrNull{genre->genre.searchKeys().any{it==key}}
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
