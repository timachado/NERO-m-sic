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
