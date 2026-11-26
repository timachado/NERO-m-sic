# NERO Music — critérios para 1.0.0-RC1

Status: estabilização pré-RC, 0.9.66-dev. Não é aprovação de distribuição.

Correções 0.9.66: unificação dos comandos de reprodução do miniplayer, player, letras e widget; pausa durante buffering, nova preparação após erro e reinício após fim da faixa por ação explícita. Mensagens para falhas de rede, HTTP, arquivo e permissão. Sem novas tentativas automáticas, sem alteração da fila ou fonte de streaming. Testes de estados adicionados; teste físico de rede móvel, Wi-Fi, modo avião, retorno da conexão e segundo plano pendente.

Correções 0.9.65: metadados do MediaController por eventos, atualização de posição somente em primeiro plano durante reprodução; atualização inicial ao retomar, listener removido ao sair. Transição de imagem preservada no player grande e desativada em miniaturas. Sem alterações de tamanho, qualidade, cache ou serviço de reprodução. Medições de CPU, memória, fluidez e validação de PiP/segundo plano ainda pendentes em aparelho.

Correções 0.9.64: um único timeout de consulta de assinatura, com ausência de resposta tratada como não verificada; tela de planos distingue catálogo experimental e release sem catálogo. Sem mudanças no checkout, no SQL ou na fonte musical.

## Evidência de CI
Execução #299 (commit 708fc9a21e069280aa965eba8be1c7ac6b331c31): assembleDebug, lintDebug, testDebugUnitTest e verificação de release sem assinatura concluídos com sucesso. Execuções #292–298 também concluídas com sucesso. Isso não comprova testes em aparelho, configuração de produção ou assinatura comercial. A nova versão acrescenta teste Python da referência Commerce Connect ao CI; resultado ainda pendente.

## Bloqueios atuais
- NativeMusicCatalog no release retorna enabled=false; Smart Mix e Daily Mix online dependem dessa fonte. Não habilitar o extrator experimental em distribuição comercial. Definir fonte autorizada e verificar paridade de recursos antes de vender benefícios.
- O checkout é uma prévia desativada. O responsável pela T.I. Machado configurará a loja manualmente; nenhum produto, gateway ou configuração de produção foi modificado. A integração servidor-servidor, ativação, trial, renovação, estorno e isolamento ainda exigem homologação.
- SQL de assinatura é rascunho não aplicado. Verificar estado da assinatura com duas contas de teste, expiração e falhas de rede; não liberar Premium por resposta de checkout.
- O pipeline de APK assinado é manual e restrito à main e ambiente nero-release. Não foi executado nesta revisão, não há APK comercial assinado verificado. Não mesclar nem publicar sem autorização.

## Matriz Premium no Android
- [ ] Temas: Aurora gratuito; validar Ocean, Violet, Gold e Midnight Café, logout e expiração.
- [ ] Replay: consentimento, duas contas, pausa, limpeza, 7/30/90 dias e ausência de dados antigos de outra conta.
- [ ] Backup: exportação JSON, importação na mesma conta, rejeitar outra conta e arquivo inválido, mesclar sem apagar dados, repetir sem duplicar.
- [ ] Smart Mix: seleção de até seis artistas, modos, limite de faixas, exclusões, falhas de rede.
- [ ] Daily Mix: quatro momentos, rotação diária, ausência de duplicatas e de resultados fictícios.
- [ ] Assinatura: ausência, ativa, expirada, revogada, trial e carência; falhar de forma segura sem servidor.
- [ ] Commerce: seletor de planos, botão de contratação desativado até homologação, nenhuma cobrança no teste.


## Testes de aceitação em Android
- [ ] Atualização sobre APK anterior sem perda de biblioteca ou preferências.
- [ ] Busca, artistas, álbuns, imagens em alta resolução e biografias em português.
- [ ] Reprodução local, catálogo autorizado, fila, rádio, favoritos e playlists.
- [ ] Downloads autorizados, Wi-Fi obrigatório quando ativado, qualidade e reprodução offline.
- [ ] Fechar pelos recentes, reabrir, controles do sistema, retomada, bloqueio de tela.
- [ ] Bluetooth, Android Auto e Chromecast compatível em equipamentos reais.
- [ ] Login Google, renovação de sessão, foto/nome, logout e compartilhamento.
- [ ] Preferências de qualidade e navegação da conta.

## Segurança e privacidade
- [ ] Revisar permissões, logs, credenciais de assinatura, isolamento de dados e políticas de acesso.
- [ ] Validar inventário de dados remotos, retenção, revogação de sessões e exclusão autenticada no servidor.
- [ ] Confirmar canal de atendimento e procedimento de solicitação de exclusão; fluxo atual apenas copia uma mensagem e abre o site, sem enviar ou apagar dados.
- [ ] Aprovar e publicar política de privacidade e termos definitivos, com identificação e contato do responsável.
- [ ] Verificar licença de marca, catálogo, letras, imagens, streaming e downloads antes da distribuição.

## Empacotamento comercial
- [ ] Definir fonte autorizada de conteúdo. O catálogo experimental e alguns recursos de integração estão disponíveis somente no debug.
- [ ] Validar o build release real: não presumir paridade com debug.
- [ ] Assinar com chave de produção protegida, verificar assinatura e atualização em aparelho.
- [ ] Gerar APK, SHA-256, notas de versão e página de distribuição direta.
- [ ] Aprovação explícita antes de publicar ou mesclar a branch.

Não habilitar extração experimental em release para contornar licenciamento. Não distribuir APK debug como versão comercial.

## 0.9.68-dev — revisão visual de gravação do Android
Replay em tela cheia rolável, com Fechar sempre visível; botão direto de Daily Mix reutiliza a consulta manual existente, com feedback de progresso; Discovery oferece busca pelos artistas quando a consulta não encontra álbuns; rádio local explica a ausência de arquivos e orienta uso da rádio online experimental quando disponível. Não altera o catálogo, não habilita fonte experimental no release, não altera dados ou assinatura. Validação visual em aparelho e compilação desta versão ainda pendentes.
