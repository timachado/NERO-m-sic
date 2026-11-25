# NERO Music

O som da sua liberdade.

Aplicativo Android da T.I. Machado. Identidade Aurora Neon.

## 0.2.0-dev
- Biblioteca local, busca local, favoritos, Media3 e player em segundo plano.
- Home de descoberta, gêneros, pesquisa de áudios musicais no Wikimedia Commons com filtro conservador de licença, créditos e reprodução online.
- Busca no YouTube aberta por link no cliente oficial/navegador; não extrai áudio nem baixa vídeos.
- Downloads do catálogo aberto no armazenamento privado do app, com metadados de licença e reprodução offline.
- Catálogo comercial, grade de álbuns, busca YouTube interna e playlists próprias ainda pendentes.
- APK de teste via Actions; a nova versão precisa compilar e ser validada em aparelho real.

## 0.2.1-dev
- Pesquisa direcionada ao YouTube Music oficial por deep link, com fallback para navegador, sem extração de áudio.
- Busca do catálogo aberto e downloads autorizados mantidos.
- Cores explícitas Aurora Neon na navegação inferior.
- Resultados internos do YouTube Music e downloads desse serviço não implementados; downloads oficiais são gerenciados pelo próprio YouTube Music.

## 0.3.0-dev
- Exploração interna de metadados por músicas, álbuns e artistas, disponível apenas em builds debug, para avaliação não comercial do MusicBrainz. A API pública gratuita não é habilitada em release.
- Grade de álbuns e consulta de capas via Cover Art Archive quando disponíveis; as capas pertencem aos respectivos titulares.
- Limite de uma solicitação por segundo e cache de consultas em memória.
- Reprodução de resultados comerciais direcionada ao YouTube Music oficial. Não há streaming nem download de faixas comerciais pelo NERO.
- Catálogo aberto, downloads autorizados e player local mantidos.
- Uso comercial da API e dos conteúdos depende das permissões e condições aplicáveis.

## 0.3.1-dev
- Home com carrossel de álbuns e seleção editorial por artista, somente no build debug; não representa ranking nem recomendação personalizada.
- Busca online na aba Buscar, com filtro para manter a pesquisa local.
- Atalhos de catálogo com termo pré-preenchido e cartões de gêneros Aurora Neon.
- Preserva o catálogo aberto e downloads autorizados; YouTube Music permanece externo. Lançamentos reais, personalização e catálogo comercial integrado ainda pendentes.

## 0.4.0-dev
- Playlists persistentes de faixas locais: criar, renomear, adicionar, retirar, excluir lista sem apagar áudio e reproduzir em fila.
- Histórico das últimas 100 faixas distintas reproduzidas no serviço Media3, inclusive em segundo plano; reprodução direta de registros locais disponíveis.
- Histórico e playlists armazenados no dispositivo, sem conta ou servidor. Histórico de reprodução externa no YouTube Music não é capturado. Playlists de conteúdo online ainda pendentes.
- APK de desenvolvimento, sujeito a compilação e teste em dispositivo.

## 0.5.0-dev — conta NERO
- Tela Minha conta, acesso como visitante, cadastro e login por e-mail/senha, recuperação de senha, verificação de e-mail, login Google via Credential Manager, logout e exclusão da própria conta (pode exigir reautenticação).
- Firebase Auth é opcional até configuração real; nenhum login simulado. Dados musicais permanecem locais; sincronização não implementada.
- Para habilitar: crie projeto Firebase exclusivo do NERO, registre Android com applicationId br.com.timachado.neromusic, habilite Email/Password e Google em Authentication, configure SHA-1/SHA-256 do certificado que efetivamente assinará o APK, baixe google-services.json atualizado. Coloque em app/google-services.json (ignorado pelo Git) ou configure o secret de Actions NERO_GOOGLE_SERVICES_JSON com o conteúdo do arquivo. Não use service accounts, chaves privadas ou credenciais administrativas no cliente. Para Google, mantenha uma chave de assinatura estável entre builds e registre as impressões do certificado; não publique uma chave de assinatura no repositório. A configuração do Firebase e os testes de login real ainda estão pendentes.

## 0.5.1-dev
- Tela informativa de planos Free/Premium acessível em Minha conta, com benefícios futuros identificados como em desenvolvimento.
- Sem checkout, preços, cobrança ou desbloqueio Premium. Distribuição direta prevista pelo site da T.I. Machado; ver docs/subscriptions.md.
