# NERO Music — assinaturas (proposta de arquitetura)
Status: planejamento. Decisão do proprietário: distribuição direta de APK pelo site da T.I. Machado (https://timachado.ifree.page/), seguindo o modelo dos demais aplicativos. Google Play não integra o plano de lançamento atual. Nenhuma cobrança habilitada, nenhum preço aprovado, nenhum produto comercial criado.

## Produto
- NERO Free: player local, biblioteca, busca, favoritos e playlists básicas.
- NERO Premium: benefícios próprios do NERO, como ausência de anúncios do NERO (se houver publicidade própria), equalizador avançado, sincronização, playlists inteligentes, personalização e downloads somente de fontes autorizadas.
- Planos mensal e anual propostos. Preços, períodos de teste, impostos e condições de cancelamento a definir antes de publicação.
- A assinatura NERO não concede acesso ao YouTube Music Premium, nem direitos de distribuição de catálogos de terceiros.

## Identidade e autenticação
Firebase Authentication exclusivo do NERO, UID estável, login Google ou e-mail. Visitante pode utilizar funções gratuitas. Nenhum dado dos outros aplicativos deve ser modificado.

## Distribuição e cobrança
- Canal de lançamento: página oficial do NERO Music no site da T.I. Machado, com APK release assinado, versão, requisitos Android, instruções de instalação, checksum SHA-256 e política de privacidade.
- Google Sign-In continua sendo autenticação, independente da distribuição pela Google Play. Google Play Billing não faz parte do lançamento direto.
- Checkout hospedado pelo provedor de pagamentos, associado à conta NERO autenticada pelo backend. Escolher provedor e condições comerciais antes de ativar cobrança. Pix e cartão são opções a avaliar; renovação automática depende do suporte real do provedor.
- Não alterar o checkout nem as configurações dos pagamentos existentes da T.I. Machado. Nenhum produto ou cobrança real criado.
- Atualizações por HTTPS, com metadados de versão e integridade e APK assinado pela mesma chave de release, preservando o applicationId. Instalação e atualização mediante confirmação do usuário no Android; não contornar proteções do sistema.
- Nunca distribuir APK debug como release comercial. Guardar chave de assinatura com segurança e configurar SHA-1/SHA-256 no Firebase para o certificado de release.

## Entitlement no backend
- Validar o token Firebase no servidor e associar o UID à assinatura via checkout criado pelo backend. Não confiar em UID, preço, plano ou status enviados pelo cliente.
- Processar webhooks com verificação de autenticidade, proteção contra replay e idempotência; confirmar estado pela API oficial do provedor. Tratar renovação, cancelamento, expiração, falha, reembolso e revogação.
- Apenas o backend concede Premium. O estado local é cache com validade limitada, nunca fonte de autorização. Não liberar por comprovantes, retorno de checkout ou pagamentos pendentes.
- Permitir acesso à conta e gerenciamento/cancelamento da assinatura pelo canal oficial; restaurar acesso após autenticação e consulta ao backend.
- Nunca publicar credenciais administrativas, chaves privadas de assinatura ou dados privados. Antes de produção: LGPD, termos, privacidade, cancelamento, exclusão de conta, testes de isolamento e recuperação.

## Dependências e próximos marcos
1. Compilar e testar a versão de autenticação existente; configurar Firebase e certificado estável.
2. Implementar benefícios premium reais, provedor de entitlement e tela de planos sem checkout ativo.
3. Selecionar provedor de checkout recorrente, configurar sandbox isolado, backend seguro e testes de ciclo de vida.
4. Validar direitos do catálogo comercial e distribuição offline antes de cobrar por esses conteúdos.
5. Aprovar preços, assinar e validar APK release, preparar página de download e habilitar pagamentos apenas após validação.

## Referências
- https://developer.android.com/studio/publish/app-signing
- https://firebase.google.com/docs/auth/android/google-signin
