# API Campeonato de Futebol (trabalho acadêmico, entrega sexta de manhã)

Prioridade: FUNCIONAR e ser explicável. Respostas curtas. Não leia `target/`, `.idea/` nem `.git/`.
Sou iniciante: comentários curtos em português explicando o "porquê". Vou apresentar este código ao professor.

## Stack
Java 17, Spring Boot 3.5.16, Spring Data JPA, H2 em memória, Spring HATEOAS, Bean Validation, springdoc 2.8.17.
Maven: rodar `../../../Downloads/mvnw` dentro da pasta `api-futebol/` (pasta interna, onde está o pom.xml). Pacote base: `com.campeonato.api_futebol`.
O repositório tem pasta de fora (.git, README.md, postman/) e pasta de dentro (api-futebol/ com pom.xml e src/).

## Arquitetura (NÃO mudar)
- Controller -> Repository -> Model. SEM camada Service (decisão do aluno). SEM Spring Security (usar filtros próprios).
- Entidades JPA puras. HATEOAS só nos `assembler/*ModelAssembler`. Injeção por construtor.
- Erros: `exception/GlobalExceptionHandler` + `ApiError` (record: timestamp, status, erro, mensagem, campos).
  ATENÇÃO: exceções lançadas em filtros NÃO passam pelo @RestControllerAdvice; o filtro deve escrever o JSON do ApiError sozinho.
- Swagger: `config/OpenApiConfig` (título/descrição/tags). Página customizada em `static/swagger-custom.html|css`, servida na raiz `/`.
- Pacotes: model, repository, assembler, controller, exception, config. Novo pacote para filtros: `filter`.
- Entidades: Time (1:1 Estadio, 1:N Jogador, N:N Campeonato), Estadio, Jogador, Campeonato, Partida. Rotas: /times /estadios /jogadores /campeonatos /partidas (CRUD paginado + buscar-por-*).

## Parte 2: decisões FECHADAS (siga exatamente, não pergunte)

### Etapa 0 - Publicação (Render, Docker)
Dockerfile multi-stage (JDK 17 para build com mvnw, JRE 17 para rodar) dentro de `api-futebol/` + `.dockerignore`.
`server.port=${PORT:8080}`, `server.forward-headers-strategy=framework` (links HATEOAS e Swagger precisam sair em https atrás do proxy).
Configuração por variáveis de ambiente: `INITIAL_API_KEY`, `CORS_ALLOWED_ORIGINS`, `RATE_LIMIT_PER_MINUTE`, `SPRING_H2_CONSOLE_ENABLED`.

### Etapa 1 - API Key (`filter/ApiKeyFilter`, OncePerRequestFilter)
- Entidade `ApiKey` (id, nome, hash SHA-256 da chave, prefixo de 8 chars, ativa, criadaEm). Sem níveis de acesso: toda chave válida e ativa tem as mesmas permissões. Nunca guardar a chave em texto puro; a chave completa só aparece na resposta do POST que a cria.
- Chave gerada: "fut_" + 32 bytes aleatórios (SecureRandom) em hex.
- A chave inicial vem de `app.initial-api-key` (env `INITIAL_API_KEY`, valor padrão só para dev). No startup, criar no banco com o nome "inicial" (o H2 em memória reinicia vazio). Ela serve para criar as demais chaves.
- Endpoints (todos exigem chave válida): `POST /api-keys` {nome}, `GET /api-keys` paginado sem hash, `DELETE /api-keys/{id}` revoga (marca ativa=false, responde 204).
- Regras: todo GET é público, exceto /api-keys. POST/PUT/DELETE em /times /estadios /jogadores /campeonatos /partidas e todos os endpoints de /api-keys exigem chave válida e ativa. Sem chave, inválida ou revogada -> 401. NÃO existem níveis de acesso nem resposta 403.
- Sempre liberados: `/`, `/swagger-*`, `/swagger-ui/**`, `/v3/api-docs/**`, `/h2-console/**`, arquivos estáticos e OPTIONS.
- Swagger: esquema de segurança apiKey no header `X-API-Key` (botão Authorize) e resposta 401 documentada.

### Etapa 2 - Rate limit (`filter/RateLimitFilter`)
- Em memória (ConcurrentHashMap), janela fixa de 1 minuto, sem dependência externa. Cliente = IP (usar o primeiro valor de `X-Forwarded-For` se existir, senão `getRemoteAddr()`).
- Limite em `app.rate-limit.requests-per-minute` (env `RATE_LIMIT_PER_MINUTE`, padrão 60). Não limitar estáticos nem Swagger.
- Toda resposta: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (segundos). Ao exceder: 429 com ApiError + `Retry-After` (segundos).

### Etapa 3 - CORS (`config/CorsConfig`)
- Usar um bean `CorsFilter` registrado com a MAIOR precedência, para que respostas 401/429 geradas pelos filtros também levem os cabeçalhos CORS (senão o navegador mostra erro de CORS em vez do erro real).
- Origens: `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`, lista separada por vírgula). Padrão: http://localhost:3000, http://localhost:5173, http://localhost:5500, http://127.0.0.1:5500, http://localhost:8080. NUNCA usar "*".
- Métodos: GET, POST, PUT, DELETE, OPTIONS. Headers aceitos: Content-Type, X-API-Key, X-Idempotency-Key, X-API-Version.
- Headers expostos: Location, Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset, Idempotent-Replayed. maxAge 3600.

### Etapa 4 - Idempotência (`filter/IdempotencyFilter`, roda depois do ApiKeyFilter)
- Só em POST de /times /estadios /jogadores /campeonatos /partidas (NÃO em /api-keys). Header `X-Idempotency-Key` opcional: sem ele, processa normalmente.
- Tabela `IdempotencyRecord` (chave única, método+caminho, hash SHA-256 do corpo, status, corpo da resposta, Location, criadoEm).
- Mesma chave + mesmo corpo/rota: devolve a resposta guardada (mesmo status, corpo e Location) com `Idempotent-Replayed: true`, SEM executar de novo.
- Mesma chave com corpo ou rota diferente: 422 (ApiError). Mesma chave ainda em processamento: 409.
- Guardar só respostas 2xx. Usar wrapper que permita ler o corpo da requisição mais de uma vez.

### Etapa 5 - Versionamento por header `X-API-Version`
- Em `GET /times` e `GET /times/{id}`. v1 = formato atual. v2 = record `TimeV2Response`: id, nome, sigla, anoFundacao, estadio {nome, capacidade}, totalJogadores, totalCampeonatos, mais links HATEOAS.
- Sem header = v1. Valor inválido (ex.: 3 ou abc) = 400 (ApiError). Devolver o header `X-API-Version` na resposta. Documentar o header no Swagger.

### Etapa 6 - Documentação
Swagger (esquema X-API-Key, headers, respostas 401/422/429), README (seção Parte 2 com exemplos curl) e coleção Postman (variáveis apiKey e idempotencyKey, requests novos).

### Etapa 7 - Frontend (HTML+CSS+JS puro, sem build) em `docs/` na raiz do repositório (GitHub Pages: branch main, pasta /docs)
- `config.js` com `API_BASE_URL`. Campo "Chave de API" digitado pelo usuário, guardado em sessionStorage (NUNCA colocar chave no código).
- Lista times paginada seguindo os links HATEOAS (`next`/`prev`), cadastro de time enviando `X-Idempotency-Key` (crypto.randomUUID()), seletor de versão v1/v2, mensagens amigáveis para 400/401/429.

## Regras de trabalho
- Faça UMA etapa por vez, só quando eu pedir. Edite só o necessário; não reescreva arquivos inteiros. Não crie testes automatizados extensos.
- Após cada etapa: compile (`./mvnw -q -DskipTests compile`), suba a aplicação, teste com curl, mostre o resultado em até 10 linhas e faça um commit.
- Ao final de cada etapa: 5 linhas "o que foi feito e por quê" (para eu explicar ao professor).
- Se um erro for longo, mostre só as linhas `Caused by`.
