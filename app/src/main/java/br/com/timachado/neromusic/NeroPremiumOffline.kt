package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val LocalNeroPremiumOffer=staticCompositionLocalOf<()->Unit>{{}}
internal const val premiumOfflineMessage="Ouça offline com NERO Premium."
internal class OfflinePremiumRequired:IllegalStateException(premiumOfflineMessage)
internal fun offlineDownloadAllowed(premium:Boolean,sourceAuthorized:Boolean)=premium&&sourceAuthorized
internal fun requireOfflinePremium(owner:String?){
    if(owner==null||NeroSupabase.activeUid.value!=owner||!NeroPlaybackAccess.allowed())throw OfflinePremiumRequired()
}
// Streaming availability is not evidence of offline distribution rights.
@Suppress("UNUSED_PARAMETER")
internal fun nativeOfflineAuthorized(hit:NativeMusicHit):Boolean=false

@Composable
internal fun rememberOfflinePremium():Boolean{
    val signal by NeroPlaybackAccess.premium.collectAsState()
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    return signal&&ready&&uid!=null&&NeroPlaybackAccess.allowed()
}
@Composable
internal fun PremiumOfflineCard(){
    val onPremium=LocalNeroPremiumOffer.current
    val colors=MaterialTheme.colorScheme
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=colors.surfaceVariant)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.WorkspacePremium,null,tint=colors.primary,modifier=Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("NERO PREMIUM",color=colors.primary,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
            }
            Text("Sua música, onde você estiver.",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Ouça sem internet com os downloads disponíveis no Premium.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
            Button(onClick=onPremium,modifier=Modifier.fillMaxWidth()){Text("Conhecer NERO Premium")}
        }
    }
}
