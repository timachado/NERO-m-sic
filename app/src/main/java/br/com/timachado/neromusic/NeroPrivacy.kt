package br.com.timachado.neromusic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class NeroPrivacyPage { DATA, TERMS, ACCOUNT }

@Composable
internal fun NeroPrivacyDialog(page:NeroPrivacyPage,onClose:()->Unit){
    val context=LocalContext.current
    val title=when(page){NeroPrivacyPage.DATA->"Privacidade e seus dados";NeroPrivacyPage.TERMS->"Termos de uso";NeroPrivacyPage.ACCOUNT->"Solicitar exclusão da conta"}
    AlertDialog(onDismissRequest=onClose,title={Text(title)},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            when(page){
                NeroPrivacyPage.DATA->{
                    Text("Dados no aparelho",fontWeight=FontWeight.Bold)
                    Text("Favoritos, playlists online, artistas seguidos e álbuns salvos são vinculados à conta e podem ser sincronizados quando o serviço estiver disponível. O recurso Ouça novamente mantém até 100 faixas recentes por conta neste aparelho e pode ser pausado em NERO Cloud. O histórico online só é enviado para sincronização quando você ativa essa opção separadamente. Pausar o histórico não apaga os registros anteriores; use a opção de limpar histórico da conta. Músicas ocultadas das recomendações também podem ser sincronizadas. Preferências do aparelho, arquivos locais e downloads não são enviados à biblioteca na nuvem.")
                    Text("Login e compartilhamento",fontWeight=FontWeight.Bold)
                    Text("Ao entrar com Google, o NERO usa o Supabase para autenticação e compartilhamento. Nome, e-mail e foto disponíveis podem ser exibidos no perfil. A sessão é armazenada com criptografia do Android. Ao compartilhar uma música ou playlist, o NERO gera um link válido por sete dias. A prévia de título, artista e quantidade de faixas pode ser consultada por quem possui o link. A abertura do conteúdo exige conta autenticada; arquivos de áudio e dados de login não são incluídos.")
                    Text("NERO Together e planos",fontWeight=FontWeight.Bold)
                    Text("Salas privadas mantêm nome, participantes, referências de faixas e votos. O convite permite acesso de contas autenticadas enquanto a sala estiver disponível, por até 24 horas. Cada participante reproduz seu próprio áudio. O serviço registra o estado do plano e a elegibilidade para uma experiência Premium por conta. A expiração de um link ou sala não significa exclusão imediata do registro no servidor.")
                    Text("Serviços externos",fontWeight=FontWeight.Bold)
                    Text("Pesquisas, reprodução online, letras, imagens e biografias podem enviar consultas e dados de conexão às respectivas fontes de conteúdo. O acesso a arquivos locais depende da permissão de áudio do Android. O Radar opcional consulta nomes de artistas e gêneros; alertas em segundo plano dependem de autorização e podem ser desligados na home.")
                    Text("Controle dos seus dados",fontWeight=FontWeight.Bold)
                    Text("Você pode sair da conta, revogar permissões nas configurações do Android e solicitar atendimento sobre dados pessoais. Limpar os dados do aplicativo também remove a biblioteca local e os downloads; isso não exclui a conta remota.")
                    Text("Atividade opcional",fontWeight=FontWeight.Bold)
                    Text("Se você permitir, registramos sua conta, um identificador aleatório por instalação e a data do último uso para estatísticas administrativas. Não enviamos localização, identificadores de hardware, faixas ou histórico de reprodução para essa finalidade. Você pode desligar o compartilhamento na conta; o aplicativo solicita a remoção do registro quando conectado. Registros sem atividade são removidos após 90 dias, pela rotina diária do servidor.")
                    Text("Avisos de atualização e descobertas da grade são opcionais e independentes. As descobertas consultam uma seleção pública do catálogo, sem utilizar seu histórico ou sua conta; o controle de itens já vistos é mantido no aparelho. As fontes externas recebem dados de conexão necessários à consulta. Consulte o atendimento da T.I. Machado para informações sobre o tratamento dos seus dados.",fontSize=12.sp)
                }
                NeroPrivacyPage.TERMS->{
                    Text("Uso do aplicativo",fontWeight=FontWeight.Bold)
                    Text("O NERO Music oferece recursos locais e online. Recursos online dependem da disponibilidade e das condições das fontes externas. Use apenas conteúdos para os quais você tenha autorização.")
                    Text("Downloads e biblioteca",fontWeight=FontWeight.Bold)
                    Text("Mantenha cópias dos seus arquivos originais. O aplicativo não garante disponibilidade permanente de conteúdo de terceiros.")
                    Text("Recursos Premium dependem da situação do plano vinculado à sua conta. Consulte as condições, valores e validade apresentados na contratação antes de confirmar o pagamento. Notificações de atualização e compartilhamento de atividade são opcionais. Para suporte, dúvidas sobre planos ou dados, utilize o atendimento da T.I. Machado.",fontSize=12.sp)
                }
                NeroPrivacyPage.ACCOUNT->{
                    Text("Você pode solicitar a exclusão da sua conta NERO pelo atendimento da T.I. Machado.")
                    Text("Copie a mensagem abaixo e envie pelo canal de atendimento do site. O envio não é automático; a identidade deverá ser verificada antes da exclusão.")
                    Text("Solicito a exclusão da minha conta NERO Music e informações sobre os dados pessoais vinculados a ela.",fontWeight=FontWeight.Medium)
                    Text("Nenhum dado será apagado ao copiar a solicitação. A biblioteca local não será removida automaticamente.",fontSize=12.sp)
                }
            }
        }
    },confirmButton={
        if(page==NeroPrivacyPage.ACCOUNT)TextButton(onClick={
            val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Solicitação NERO","Solicito a exclusão da minha conta NERO Music e informações sobre os dados pessoais vinculados a ela."))
            if(!openBrowser(context,Uri.parse("https://timachado.ifree.page")))Toast.makeText(context,"Mensagem copiada. Abra o site da T.I. Machado.",Toast.LENGTH_SHORT).show()
        }){Text("Copiar e abrir site")}
        else TextButton(onClick=onClose){Text("Entendi")}
    },dismissButton={if(page==NeroPrivacyPage.ACCOUNT)TextButton(onClick=onClose){Text("Voltar")}})
}
