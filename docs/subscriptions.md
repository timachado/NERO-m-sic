# NERO Music — assinaturas (proposta de arquitetura)
Status: planejamento; nenhuma cobrança habilitada, nenhum preço aprovado, nenhum produto comercial criado.

## Produto
- NERO Free: player local, biblioteca, busca, favoritos e playlists básicas.
- NERO Premium: benefícios próprios do NERO, como ausência de anúncios do NERO (se houver publicidade própria), equalizador avançado, sincronização, playlists inteligentes, personalização e downloads somente de fontes autorizadas.
- Planos mensal e anual propostos. Preços, períodos de teste, impostos e condições de cancelamento a definir antes de publicação.
- A assinatura NERO não concede acesso ao YouTube Music Premium, nem direitos de distribuição de catálogos de terceiros.

## Identidade e autenticação
Firebase Authentication exclusivo do NERO, UID estável, login Google ou e-mail. Visitante pode utilizar funções gratuitas. Nenhum dado dos outros aplicativos deve ser modificado.

## Distribuição e cobrança
- Google Play: Google Play Billing, salvo exceções e programas aplicáveis; produtos e planos criados apenas após aprovação comercial. Usar preços e ofertas retornados pela loja, nunca valores fictícios no checkout.
- APK distribuído diretamente pelo site: avaliar checkout externo separado e provedor autorizado, com verificação de webhook. Não alterar o checkout ou a configuração de pagamentos existente da T.I. Machado. Não direcionar usuários da versão Play a checkout externo sem enquadramento nas políticas.
- Separar canais de distribuição e manter uma identidade de conta comum. Sem alterar assinatura do APK ou configurar chaves sem autorização apropriada.

## Entitlement no backend
- Validar identidade Firebase no servidor, associar compra à conta, verificar o purchase token com a Google Play Developer API e confirmar produto, pacote, estado e validade.
- Processar notificações RTDN com idempotência; reconsultar estado oficial e tratar renovação, cancelamento, carência, suspensão, expiração, reembolso e revogação.
- Apenas o backend concede Premium. Estado local é cache, nunca fonte de autorização. Não conceder acesso por compra pendente ou não verificada. Reconhecer compras válidas conforme as regras da loja.
- Restaurar compras com verificação no servidor; não reutilizar tokens entre contas. Nunca publicar credenciais administrativas, chaves de assinatura ou dados privados.
- Antes de produção: LGPD, termos, privacidade, cancelamento, exclusão de conta, testes com compras de licença, isolamento de usuários, persistência e recuperação.

## Dependências e próximos marcos
1. Compilar e testar a versão de autenticação existente; configurar Firebase e certificado estável.
2. Implementar benefícios premium reais, provedor de entitlement e tela de planos sem checkout ativo.
3. Configurar ambiente sandbox de cobrança, backend seguro e testes de ciclo de vida.
4. Validar direitos do catálogo comercial e distribuição offline antes de cobrar por esses conteúdos.
5. Aprovar preços, publicar versão assinada e habilitar pagamentos apenas após validação.

## Referências oficiais
- https://support.google.com/googleplay/android-developer/answer/9858738
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/backend
- https://developer.android.com/google/play/billing/subscriptions
