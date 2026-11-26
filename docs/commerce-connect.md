# NERO Commerce Connect — contrato de homologação
Estado: checkout desabilitado. Referência local em commerce/reference; não é um plugin WordPress nem serviço publicado. Não altera loja, gateway, Supabase ou produtos existentes.

## Contrato proposto
1. O app seleciona um ID de plano dentre os três aprovados. Um futuro endpoint autenticado valida a sessão Google/Supabase no servidor, deriva o UID e cria uma intenção aleatória de 256 bits. Nunca confiar em UID, valor ou status fornecidos pelo app.
2. O servidor guarda apenas SHA-256 do token, plano, conta e vencimento de dez minutos. A passagem ao site deve ocorrer por POST HTTPS ou mecanismo de troca seguro, sem token Google, UID ou dados pessoais em URL. Não registrar o código em analytics, logs ou referer; adotar no-store.
3. Um adaptador WooCommerce isolado, autenticado entre servidores, valida o produto NERO previamente mapeado, preço BRL em centavos e pedido. Vincula o pedido apenas uma vez, em transação. Não usar e-mail de cobrança como prova de identidade. A sessão do site precisa ser vinculada ao handoff e protegida contra CSRF. O protótipo não implementa esses adaptadores.
4. Efí/WooCommerce confirma o pagamento por consulta ao provedor e evento autenticado. A criação do pedido, retorno de checkout, comprovante ou status pendente jamais concedem Premium. O futuro processador deve verificar também valor efetivamente pago, moeda, produto, vínculo, identificador de transação e estado atual.
5. O escritor transacional de entitlement precisa registrar eventos únicos, impedir extensão duplicada, tratar eventos fora de ordem e processar expiração, estorno e revogação. Não existe nesse protótipo. O app consulta o status somente pelo RPC de leitura já existente.

## Valores
premium_monthly: 990 centavos; premium_quarterly: 2490; premium_annual: 8990. O servidor mantém catálogo próprio, sem aceitar preços do cliente. Trial de sete dias não cria pedido pago e requer registro de elegibilidade separado. Recorrência, taxas, cupons, reembolso e políticas de prazo devem ser homologados.

## Testes da referência
Executar: cd commerce/reference && python3 -m unittest -v
O SQLite é somente um protótipo isolado da operação atômica de vínculo. Não é armazenamento comercial, não substitui RLS, autenticação, persistência e segurança de produção. A integração real precisa de teste de concorrência no banco escolhido, autenticação de serviço, limpeza de intenções vencidas, auditoria sem tokens e prevenção de abuso.

## Portão obrigatório antes de ativar
- Inventariar a integração atual em modo leitura e confirmar ambiente sandbox, IDs de produtos exclusivos NERO e provedor de confirmação.
- Implementar endpoint autenticado, adaptador WooCommerce isolado e validação Efí; verificar documentação e versão efetivamente instalada.
- Homologar eventos duplicados e fora de ordem, identidade de duas contas, tentativas de reutilização, pagamento divergente, cancelamento, estorno e recuperação.
- Confirmar licenciamento e distribuição comercial do catálogo, políticas e APK release.
- Obter autorização explícita para quaisquer mudanças de produção, publicação ou cobrança. Não modificar outros aplicativos.
