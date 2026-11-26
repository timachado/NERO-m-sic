# NERO Music — decisão comercial aprovada e arquitetura
Status: preços e modelo aprovados pelo proprietário. Prévia no app; nenhum checkout, produto, cobrança ou alteração de produção habilitados.

## Oferta
| Plano | ID estável | Preço BRL | Período |
|---|---|---:|---|
| Free | free | 0,00 | gratuito |
| Premium mensal | premium_monthly | 9,90 | 1 mês |
| Premium trimestral | premium_quarterly | 24,90 | 3 meses |
| Premium anual | premium_annual | 89,90 | 12 meses |

- Experiência de 7 dias sem cartão obrigatório e sem cobrança automática no fim. Uma experiência por conta elegível, controlada pelo servidor; condições antifraude e elegibilidade precisam de homologação.
- Carência proposta e aprovada de 48 horas para falhas de renovação; política definitiva deve distinguir expiração de revogação, reembolso e fraude. Não conceder carência apenas por relógio local.
- Avisos de vencimento 3 e 1 dias antes, sujeitos a consentimento e implementação.
- Free mantém todas as funcionalidades já aprovadas: login Google, biblioteca, favoritos, playlists, sincronização básica e descoberta/mixes básicos. Não bloquear recursos existentes.
- Premium adicionará níveis avançados de Smart Mix/Daily Mix, temas exclusivos, personalização do player, estatísticas, backup/exportação avançada e suporte prioritário. Cada benefício deve estar implementado e validado antes da venda.
- Duo e Family reservados para expansão futura.
- Cancelamento sem apagar biblioteca e preferências. Termos, privacidade e direito de arrependimento aplicável devem ser documentados antes de produção.
- NERO Premium é serviço próprio, não YouTube Music Premium; não cobra acesso a conteúdo de terceiros sem autorização, nem promete remover anúncios de terceiros ou downloads não autorizados.

## Identidade
Login Google via Supabase, UID autenticado estável como NERO ID interno; visitante separado. Não usar e-mail como prova de titularidade. Nenhum dado de outros aplicativos será alterado.

## Distribuição e cobrança
- APK comercial assinado pelo site https://timachado.ifree.page/; manter applicationId, assinatura e integridade do arquivo.
- Contratação pela loja T.I. Machado, aproveitando a infraestrutura existente WooCommerce/Efí após verificar compatibilidade. Pix e cartão conforme meios habilitados; renovação automática apenas após homologação.
- Não alterar produtos, checkout, credenciais ou configurações de pagamentos existentes. Nenhum produto criado nesta etapa.
- Checkout deve vincular a conta autenticada por identificador opaco de uso limitado; preço e plano validados no servidor. Não enviar UID, token Google ou sessão em URLs externas.
- Retorno de checkout não comprova pagamento. Processar eventos autenticados, verificar estado no provedor e tratar duplicidade, replay, reembolso, revogação, cancelamento, expiração e renovação.
- Histórico, gerenciamento e cancelamento pelo canal oficial; integrar Portal do Cliente/MACH Care somente após autorização e validação.
- A recuperação no app consulta o estado do servidor; não recria compras nem concede Premium localmente.
- RPC nero_plan_read_v1 legado retorna somente status. O contrato estendido proposto em supabase/setup/nero_account_plan_details_draft.sql fornece fase, plano, vencimento e recorrência apenas se disponíveis. A tela aceita respostas legadas. Nenhum SQL aplicado. A experiência e a carência são apenas estados consultáveis; o escritor transacional, o controle de uso único da experiência e os webhooks ainda precisam de implementação e homologação.
- Não liberar benefício a partir de retorno, comprovante, status pendente ou preço informado pelo cliente. Testes de isolamento, idempotência e ciclo de vida em sandbox antes de produção.

## Commerce Connect
- Contrato e referência de vínculo de pedido em docs/commerce-connect.md e commerce/reference. Seletor de plano no Android é apenas prévia; checkout, adaptadores e processamento de pagamento ainda não estão ativos.

## Portões de lançamento
1. Implementar e testar benefícios próprios do NERO.
2. Homologar fonte comercial autorizada do catálogo e downloads.
3. Validar integração isolada da loja, backend de assinatura e segurança.
4. Validar APK release assinado, termos, privacidade e fluxos de atendimento.
5. Ativar oferta e publicação somente com autorização específica.

## 0.9.85-dev — trial e estado de assinatura
A tela de planos consulta a assinatura ativa e permite solicitar trial de sete dias somente para conta Free verificada pelo servidor. RPC de trial em `supabase/setup/nero_trial_start_draft.sql` para staging; ledger único, transação atômica, relógio do servidor e sem cobrança. O cliente reconsulta a assinatura após a solicitação. Ainda não aplicado, não homologado. A integração WooCommerce/Efí exige acesso administrativo autorizado, IDs de produtos NERO, confirmação do ambiente de teste e webhook verificado. Checkout pago continua desabilitado.

## Decisão vigente 0.9.87-dev
A contratação abre a página pública do NERO Music no site, onde o próprio Core oferece os planos e checkout. Sem vinculação manual de conta nem código na URL. Esta decisão substitui propostas anteriores de handoff manual. A publicação e a ativação comercial são administradas pelo proprietário. Não presumir que a página esteja publicada ou o backend em produção.
