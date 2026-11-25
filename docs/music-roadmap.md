# Prioridade funcional antes da comercialização

Decisão: congelar a preparação comercial ativa até validar o núcleo musical.

1. Biblioteca organizada em músicas, álbuns, artistas, gêneros, playlists, favoritos, downloads e histórico; capas reais e organização por faixa. Local e catálogo online devem ser claramente diferenciados.
2. Player completo com letras sincronizadas, busca de letras mediante fonte autorizada, indicação de ausência, rolagem e destaque da linha atual.
3. Catálogo online com páginas de artistas/álbuns, faixas, capas, estilos, lançamentos, tendências e pesquisa integrada; fontes e direitos de reprodução/download/letras precisam ser confirmados antes de comercializar.
4. Fila, playlists e favoritos de conteúdo autorizado, testes em Android, acessibilidade, desempenho, estabilidade, recuperação de falhas.
5. Só depois: ativar autenticação, serviços de nuvem e assinaturas; concluir validação comercial, versão assinada e publicação.

0.8.0-dev implementa biblioteca local por categorias, capas do aparelho e importação LRC/TXT. Ainda não oferece busca automática de letras, catálogo comercial completo ou integração de reprodução direta com YouTube Music. O pipeline release já existente fica reservado, sem execução comercial.

## Referências funcionais confirmadas
- Echo Music: referência principal para descoberta ampla, player, letras sincronizadas, reprodução offline e navegação musical. Seu repositório declara finalidade não comercial; não copiar a integração não oficial de streaming/download nem assumir direitos sobre conteúdo.
- Spotify: descoberta personalizada, playlists, fila e novidades.
- Apple Music: biblioteca organizada, discografia, apresentação de álbuns e letras em destaque.
- MusicKit para Android é opção oficial a avaliar para usuários autorizados com assinatura Apple Music; não equivale a conceder esse serviço pela assinatura NERO.
- 0.8.1-dev: detalhes experimentais do catálogo MusicBrainz por ID, discografia de artistas, escolha de edição de álbum e faixas numeradas; reprodução externa permanece.
Fontes: https://github.com/EchoMusicApp/Echo-Music ; https://developer.apple.com/musickit/ ; https://musicbrainz.org/doc/MusicBrainz_API

## NERO Transfer
Importação de dados pessoais de playlists pelo arquivo JSON exportado pelo Spotify, sem copiar áudio, acessar credenciais ou depender da API. Preserva ordem, duplicatas e referências sem correspondência; toca somente arquivo local com correspondência única de título, artista e álbum quando informado. Conexão oficial OAuth para migração futura depende de acesso aprovado; modo de desenvolvimento Spotify limita a 5 usuários autorizados e exige Premium do proprietário. Não confundir portabilidade de dados com licença de streaming. A declaração não comercial do Echo não concede direitos sobre conteúdo; o NERO pretende cobrar assinatura, portanto direitos e licença GPL de eventual código reutilizado precisam ser respeitados.

## Referência demonstrada pelo usuário — 25/11/2026
Gravação Echo Music (87 segundos) e print do player recebidos no chat. A referência funcional observada contém carrosséis de sucessos, comunidade, lançamentos, nostalgia, momentos e gêneros; pesquisa por cartões, reconhecimento por microfone; biblioteca com abas e seleção Spotify; estatísticas, escuta em grupo, retrospectiva, tradução de letras e extensões. O print mostra modos Song/Video/Music Flow, capa ampla, download, preferências, controles, fila, timer, shuffle e repeat. A gravação mostra a interface e não prova a operação de cada recurso. NERO deve preservar identidade Aurora Neon e priorizar implementação real. Não copiar marca, arte ou interfaces proprietárias literalmente.
- 0.8.9: redesenho adaptativo do player e temporizador na sessão do serviço de áudio, capas no miniplayer. Preserva letras, fila, equalizador, rádio, favoritos e seek. Nenhum modo de vídeo, fluxo visual ou download indisponível apresentado como implementado.
- Próximas etapas: organizar home por carrosséis de conteúdo verificável; pesquisa por gênero e momento; estatísticas reais do histórico; integração autorizada de áudio, letras e reconhecimento musical; escuta em grupo e retrospectiva depois de validar catálogo e privacidade. Não iniciar comercialização antes de validar funcionalidades e direitos.

## Segunda referência demonstrada pelo usuário — Spotify
Vídeo de 121 segundos: início com recomendações, recentes, estações e álbuns; busca por categorias e sugestões; biblioteca com capas, playlists e opção de importar; página de playlist com capa composta, informações, reprodução e shuffle; player com controles, informações de artista, créditos, prévia e tela de letras. Anúncios aparecem na gravação, mas não são requisito assumido do NERO. A gravação demonstra interface, não licenças ou funcionamento de todas as integrações. 0.9.0 implementa organização visual e controles das playlists locais; home personalizada avançada, informações/ créditos verificáveis, letras online, vídeo e reconhecimento permanecem no roadmap.

## Pesquisa e portabilidade
Importação exclusivamente Spotify → NERO. Busca dentro do NERO, resultados categorizados, possibilidade de guardar referências online sem arquivo local. A busca via YouTube Data API oficial pode ser integrada por backend com credenciais protegidas e cotas; não equivale ao catálogo YouTube Music nem autoriza extrair áudio, reproduzir em segundo plano ou baixar conteúdo. Não foi configurado provedor YouTube nesta versão. Referências: https://developers.google.com/youtube/data/reference/rest/v3/search/list e https://developers.google.com/youtube/terms/developer-policies-guide.
