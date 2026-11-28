package br.com.timachado.neromusic

import java.util.Locale

// Manual timing of user-supplied text. Never creates or guesses words.
internal fun neroLyricsTextLines(text:String):List<String>{
    require(text.toByteArray(Charsets.UTF_8).size<=256*1024)
    val lines=text.removePrefix("\uFEFF").lineSequence().map{it.trim()}.filter{it.isNotBlank()}.toList()
    require(lines.isNotEmpty()&&lines.size<=1000&&lines.all{it.length<=1000})
    return lines
}
internal fun neroLyricsLrc(words:List<String>,times:List<Long>,duration:Long):String{
    require(duration>0&&words.isNotEmpty()&&words.size==times.size&&words.size<=1000)
    require(words.all{it.isNotBlank()&&it.length<=1000&&!it.contains('\n')&&!it.contains('\r')})
    require(times.all{it in 0..duration}&&times.zipWithNext().all{(a,b)->b>a})
    // LRC supports millisecond precision, preserving the manually marked positions.
    val result=words.zip(times).joinToString("\n"){(word,ms)->
        String.format(Locale.ROOT,"[%02d:%02d.%03d]%s",ms/60000,(ms/1000)%60,ms%1000,word)
    }
    require(result.toByteArray(Charsets.UTF_8).size<=256*1024)
    return result
}
