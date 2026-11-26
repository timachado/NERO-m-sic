# NERO Music TV — protótipo de receptor

O objetivo é validar se o navegador da LG LF5900 (webOS 2.0) consegue abrir uma interface NERO e executar o player oficial incorporado. Este protótipo não altera o APK 185 e não é um receptor Cast instalado.

## Teste inicial
Hospede index.html em uma origem HTTPS de teste autorizada. Abra a URL no navegador da TV. Informe um ID de vídeo autorizado para incorporação ou use a URL com parâmetros v (ID de 11 caracteres), title e artist (metadados opcionais codificados para URL). O estado "Reprodução confirmada pelo player" depende do evento PLAYING do IFrame API. Não confundir carregamento de página com reprodução. O progresso usa getCurrentTime/getDuration.

A interface mantém o player oficial e sua atribuição visíveis. Não extrai áudio, não oculta anúncios nem cobra pelo acesso ao player. Não transmitir URLs de mídia extraídas ou credenciais.

## Pendências / critérios
- Validar HTTPS, carregamento do IFrame API e reprodução física no navegador da LF5900. A LG documenta Developer Mode em aparelhos webOS 3.0 ou mais recentes.
- Somente após a validação escolher mecanismo autorizado de abertura do receptor, pareamento autenticado na LAN, sincronização de comandos/estado, confirmação da faixa e desconexão segura.
- Não pausar o Android antes da confirmação remota. Não prometer abertura automática em TVs sem receptor instalado ou mecanismo de lançamento suportado.
- Publicação LG, integração Android e hospedagem ainda não realizadas; não lançar como Premium ou versão comercial.
- Referências: https://webostv.developer.lge.com/develop/tools/cli-dev-guide ; https://developers.google.com/youtube/iframe_api_reference ; https://developers.google.com/youtube/terms/developer-policies
