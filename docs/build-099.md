# 0.9.9-dev — correção de compilação

O build 45565782682 da 0.9.8 falhou em checkDebugDuplicateClasses. Foram identificadas 101 classes duplicadas entre protobuf-javalite 4.35.1 e protolite-well-known-types 18.0.0. A correção candidata exclui o AAR legado apenas nas configurações debug, mantendo Firebase e a configuração release. É necessário verificar build, lint e testes, e posteriormente validar autenticação, Firestore e reprodução em aparelho. Não altera o catálogo nem ativa serviços externos.
