package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

private const val BACKUP_LIMIT=320000
internal fun backupCollections(snapshot:CloudLibrarySnapshot)=snapshot.copy(history=emptyList(),hiddenSongs=emptySet())
internal fun backupOwner(uid:String):String{
    NeroAccountLibraryStore.fileName(uid)
    return MessageDigest.getInstance("SHA-256").digest(("nero-backup-v1:"+uid.lowercase(java.util.Locale.ROOT)).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
}
internal fun mergeNeroBackup(current:CloudLibrarySnapshot,backup:CloudLibrarySnapshot):CloudLibrarySnapshot{
    val byId=backup.playlists.associateBy{it.id}
    val lists=current.playlists.map{list->list.copy(songs=(list.songs+byId[list.id]?.songs.orEmpty()).distinctBy{it.id})}
    val result=current.copy(
        favorites=(current.favorites+backup.favorites).distinctBy{it.id},
        playlists=lists+backup.playlists.filter{row->lists.none{it.id==row.id}},
        artists=(current.artists+backup.artists).distinctBy(::nativeArtistKey),
        albums=(current.albums+backup.albums).distinctBy{it.id}
    )
    // Existing history, hidden preferences, playlist names and order are retained.
    return result
}
private fun backupBytes(uid:String,snapshot:CloudLibrarySnapshot):ByteArray =
    JSONObject().put("format","nero-backup-v1").put("owner",backupOwner(uid))
        .put("library",encodeCloudLibrary(backupCollections(snapshot))).toString()
        .toByteArray(Charsets.UTF_8).also{require(it.size<=BACKUP_LIMIT)}
private fun parseBackup(uid:String,bytes:ByteArray):CloudLibrarySnapshot{
    require(bytes.size in 1..BACKUP_LIMIT)
    val json=JSONObject(bytes.toString(Charsets.UTF_8))
    require(json.getString("format")=="nero-backup-v1"&&json.getString("owner")==backupOwner(uid))
    return backupCollections(decodeCloudLibrary(json.getJSONObject("library")))
}
private fun readLimited(context:Context,uri:Uri):ByteArray{
    require(uri.scheme=="content")
    return requireNotNull(context.contentResolver.openInputStream(uri)).use{input->
        val out=ByteArrayOutputStream();val buf=ByteArray(8192)
        while(true){
            val n=input.read(buf);if(n<0)break
            if(n==0)continue
            require(out.size()+n<=BACKUP_LIMIT);out.write(buf,0,n)
        }
        out.toByteArray()
    }
}
@Composable
internal fun NeroBackupPremium(uid:String,enabled:Boolean){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember(uid){mutableStateOf(false)}
    var pendingFile by remember(uid){mutableStateOf(false)}
    var notice by remember(uid){mutableStateOf<String?>(null)}
    var preview by remember(uid){mutableStateOf<CloudLibrarySnapshot?>(null)}
    var exportConfirm by remember(uid){mutableStateOf(false)}
    fun active()=NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        pendingFile=false
        if(uri!=null&&active()){
            busy=true;scope.launch{
                try{
                    require(verifiedPremium(context,uid))
                    withContext(Dispatchers.IO){
                        require(active()&&uri.scheme=="content")
                        val snapshot=requireNotNull(NeroAccountLibraryStore.read(context,uid)).pending
                        val bytes=backupBytes(uid,snapshot)
                        require(active())
                        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use{it.write(bytes)}
                    }
                    notice="Backup exportado."
                }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível exportar. O arquivo escolhido pode estar vazio ou incompleto."}
                finally{busy=false}
            }
        }
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        pendingFile=false
        if(uri!=null&&active()){
            busy=true;scope.launch{
                try{
                    require(verifiedPremium(context,uid))
                    val snapshot=withContext(Dispatchers.IO){parseBackup(uid,readLimited(context,uri))}
                    require(active());preview=snapshot
                }catch(e:CancellationException){throw e}catch(_:Exception){notice="Backup inválido, de outra conta, indisponível ou assinatura não confirmada."}
                finally{busy=false}
            }
        }
    }
    Text("NERO Backup Premium",fontSize=18.sp,fontWeight=FontWeight.Bold)
    Text("Exporte favoritos, playlists, artistas e álbuns online da conta. O arquivo JSON não é criptografado: guarde-o em local privado. Não inclui músicas, tokens, histórico ou estatísticas.",fontSize=12.sp,color=Color.LightGray)
    Text("Restauração somente na mesma conta Google. Os registros atuais são preservados; alterações serão sincronizadas pelo NERO Cloud quando disponível.",fontSize=12.sp,color=Color.LightGray)
    OutlinedButton(enabled=enabled&&!busy&&!pendingFile,onClick={exportConfirm=true},modifier=Modifier.fillMaxWidth()){Text("Exportar backup da biblioteca")}
    OutlinedButton(enabled=enabled&&!busy&&!pendingFile,onClick={
        if(active()){busy=true;notice=null;scope.launch{
            try{
                if(verifiedPremium(context,uid)&&active()){
                    pendingFile=true
                    try{importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))}catch(e:Exception){pendingFile=false;throw e}
                }else notice="Assinatura Premium ativa não confirmada."
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível abrir o seletor de arquivos."}
            finally{busy=false}
        }}
    },modifier=Modifier.fillMaxWidth()){Text("Importar backup")}
    if(exportConfirm)AlertDialog(onDismissRequest={exportConfirm=false},title={Text("Exportar biblioteca?")},text={Text("O arquivo contém referências e nomes da sua biblioteca, além de um identificador derivado da conta. Escolha uma pasta privada. A exportação usa a cópia local mais recente disponível, incluindo alterações pendentes.")},confirmButton={TextButton(onClick={
        exportConfirm=false
        if(active()){busy=true;notice=null;scope.launch{
            try{
                if(verifiedPremium(context,uid)&&active()&&withContext(Dispatchers.IO){NeroAccountLibraryStore.read(context,uid)!=null}){
                    pendingFile=true
                    try{export.launch("nero-biblioteca.json")}catch(e:Exception){pendingFile=false;throw e}
                }else notice="Assinatura ativa ou biblioteca local não confirmada. Sincronize e tente novamente."
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível preparar a exportação."}
            finally{busy=false}
        }}
    }){Text("Escolher local")}},dismissButton={TextButton(onClick={exportConfirm=false}){Text("Cancelar")}})
    preview?.let{backup->
        AlertDialog(onDismissRequest={preview=null},title={Text("Restaurar biblioteca?")},text={Text("Arquivo desta conta: ${backup.favorites.size} favoritos, ${backup.playlists.size} playlists, ${backup.artists.size} artistas e ${backup.albums.size} álbuns. A restauração adiciona referências sem apagar a biblioteca atual. Dados pessoais de histórico não serão importados.")},confirmButton={TextButton(enabled=!busy,onClick={
            preview=null;busy=true;scope.launch{
                try{
                    require(verifiedPremium(context,uid)&&active())
                    NeroAccountSync.restorePremiumBackup(context,uid,backup)
                    notice="Biblioteca recuperada neste aparelho. Sincronização sujeita à disponibilidade do serviço."
                }catch(e:CancellationException){throw e}catch(_:Exception){notice="Restauração não concluída. Verifique o plano e os limites da biblioteca."}
                finally{busy=false}
            }
        }){Text("Adicionar à biblioteca")}},dismissButton={TextButton(onClick={preview=null}){Text("Cancelar")}})
    }
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
}
