# NERO Music — critérios para 1.0.0-RC1

Status: desenvolvimento. Não é aprovação de distribuição.

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
