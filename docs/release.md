# Distribuição direta: candidato de release NERO Music

Estado: pipeline preparado, não executado com chaves reais. Não é autorização de publicação comercial.

## Configuração manual
- Use um projeto Firebase exclusivo, com o certificado de release registrado. Não use chaves administrativas no APK.
- Prepare uma chave de assinatura de release própria do NERO, com cópia de segurança offline. Não reutilize uma chave de debug; não envie chaves ou senhas por chat.
- No GitHub, configure o ambiente nero-release, com aprovação manual e restrição à branch main, quando disponível.
- Secrets do ambiente: NERO_GOOGLE_SERVICES_JSON, NERO_RELEASE_KEYSTORE_B64, NERO_RELEASE_STORE_PASSWORD, NERO_RELEASE_KEY_ALIAS, NERO_RELEASE_KEY_PASSWORD. Base64 não é criptografia.
- Variável do ambiente NERO_RELEASE_CERT_SHA256: impressão digital SHA-256 do certificado aprovado, conferida por canal seguro.
- Execute manualmente NERO signed release candidate. O workflow compila, verifica lint, assina, valida assinatura e certificado, alinhamento, applicationId e ausência de flag debuggable. Produz somente artefato temporário para inspeção; não publica no site nem cria cobranças.

## Antes do lançamento
- A versão atual ainda tem recursos restritos a debug: catálogo MusicBrainz, equalizador, backup cloud. O candidato release não equivale ao APK experimental.
- Habilitar e testar login real, implementar exclusão integral de dados, validar autorização de Premium no servidor e integrar provedor comercial em sandbox.
- Validar direitos de catálogo e nome comercial. Não prometer streaming/downloads comerciais sem autorização.
- Testar instalação limpa e atualização com a mesma chave e versionCode crescente; APKs de debug assinados por outra chave não poderão ser atualizados diretamente pelo release. Exportar dados úteis antes de qualquer desinstalação.
- Preparar registro do desenvolvedor e do pacote no Android Developer Console ou Play Console conforme o tipo de conta. Em novembro de 2026, o prazo regional de 30/09/2026 se aplica às lojas participantes; download direto ainda não está sujeito àquela etapa, mas a expansão está prevista para 2027.
- Publicar apenas após aprovação, via HTTPS no site oficial, acompanhado de checksum SHA-256, requisitos de instalação e política de privacidade. O Android exige confirmação de instalação/atualização.

Referências: https://developer.android.com/tools/apksigner
https://developer.android.com/developer-verification/guides/faq
