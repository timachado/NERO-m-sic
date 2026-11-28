package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NeroLyricsEditor(initial:String,position:Long,duration:Long,playing:Boolean,onTogglePlay:()->Unit,
    saving:Boolean,onRestart:(()->Unit)?,onDismiss:()->Unit,onSave:(String)->Unit){
    var draft by remember{mutableStateOf(initial)}
    var words by remember{mutableStateOf<List<String>?>(null)}
    var marks by remember{mutableStateOf<List<Long>>(emptyList())}
    var error by remember{mutableStateOf<String?>(null)}
    fun validated():List<String>?=try{
        val rows=neroLyricsTextLines(draft)
        require(parseLyrics(rows.joinToString("\n"))==rows.map{LyricLine(null,it)})
        rows
    }catch(_:Exception){error="Use texto simples, um verso por linha, até 256 KB.";null}
    val timing=words
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},title={Text(if(timing==null)"Inserir letra" else "Sincronizar letra")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
                if(timing==null){
                    Text("Insira sua letra própria ou uma letra que você possa utilizar. Nenhum texto é criado automaticamente.")
                    OutlinedTextField(value=draft,onValueChange={if(it.length<=262144){draft=it;error=null}},modifier=Modifier.fillMaxWidth().heightIn(min=100.dp,max=240.dp),
                        minLines=4,label={Text("Um verso por linha")},enabled=!saving)
                    OutlinedButton(enabled=!saving&&duration>0&&draft.isNotBlank(),onClick={
                        validated()?.let{words=it;marks=emptyList();error=null}
                    },modifier=Modifier.fillMaxWidth()){Text("Marcar tempos com a música")}
                }else{
                    Text("Versos marcados: "+marks.size+" de "+timing.size)
                    LinearProgressIndicator(progress={marks.size.toFloat()/timing.size},modifier=Modifier.fillMaxWidth())
                    Text("Posição: "+android.text.format.DateUtils.formatElapsedTime(position.coerceAtLeast(0)/1000))
                    Text(timing.getOrNull(marks.size)?:"Todos os versos marcados.")
                    OutlinedButton(enabled=!saving,onClick=onTogglePlay,modifier=Modifier.fillMaxWidth()){Text(if(playing)"Pausar" else "Reproduzir")}
                    if(onRestart!=null)TextButton(enabled=!saving,onClick={onRestart();marks=emptyList();error=null}){Text("Voltar ao início da música")}
                    Button(enabled=!saving&&marks.size<timing.size&&duration>0,onClick={
                        if(position !in 0..duration||marks.lastOrNull()?.let{position<=it}==true)
                            error="Avance a reprodução antes de marcar o próximo verso."
                        else{marks=marks+position;error=null}
                    },modifier=Modifier.fillMaxWidth()){Text("Marcar verso agora")}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        TextButton(enabled=marks.isNotEmpty()&&!saving,onClick={marks=marks.dropLast(1);error=null}){Text("Desfazer")}
                        TextButton(enabled=!saving,onClick={marks=emptyList();error=null}){Text("Reiniciar")}
                        TextButton(enabled=!saving,onClick={words=null;marks=emptyList();error=null}){Text("Editar")}
                    }
                }
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
        },
        confirmButton={TextButton(enabled=!saving&&(if(timing==null)draft.isNotBlank() else marks.size==timing.size),onClick={
            if(timing==null)validated()?.let{onSave(it.joinToString("\n"))}
            else try{
                val lrc=neroLyricsLrc(timing,marks,duration)
                require(parseLyrics(lrc)==timing.zip(marks).map{(word,at)->LyricLine(at,word)})
                onSave(lrc)
            }catch(_:Exception){error="Não foi possível salvar os tempos. Revise as marcações."}
        }){Text(if(saving)"Salvando…" else "Salvar letra")}},
        dismissButton={TextButton(enabled=!saving,onClick=onDismiss){Text("Cancelar")}}
    )
}
