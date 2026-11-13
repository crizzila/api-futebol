# API Campeonato de Futebol

API RESTful desenvolvida com **Spring Boot** para gerenciar um campeonato de futebol: **times, estádios, jogadores, campeonatos e partidas**.
Projeto final do curso de Desenvolvimento de APIs com Spring Boot (**Partes 1 e 2**). A Parte 2 (publicação, chave de API, rate limit, CORS, idempotência e versionamento) está descrita na seção [Parte 2](#parte-2-publicação-e-segurança).

## Tecnologias

| Item | Versão / uso |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.16 |
| Spring Data JPA (Hibernate) | persistência / ORM |
| Banco de dados | H2 em memória |
| Spring HATEOAS | REST nível 3, formato HAL |
| Bean Validation | validação dos dados de entrada |
| SpringDoc OpenAPI | 2.8.17 (Swagger UI) |
| Maven | gerenciador de dependências |

## Como executar

Pré-requisitos: **JDK 17+** (o Maven já vem pelo wrapper `mvnw`).

```bash
./mvnw spring-boot:run        # Linux/Mac
mvnw.cmd spring-boot:run      # Windows
```

Ou rode a classe `ApiFutebolApplication` direto pelo IntelliJ.

| O que | Endereço |
|---|---|
| Documentação (Swagger customizado) | http://localhost:8080/ |
| Swagger UI padrão | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| Console do H2 | http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:futebol`, usuário `sa`, senha vazia) |

> O banco é **em memória**: os dados são apagados quando a aplicação é reiniciada.

## Modelo de dados

```
Estadio 1 ──── 1 Time 1 ──── N Jogador
                  │ N
                  │
                  N
              Campeonato 1 ──── N Partida N ──── 2 Time (mandante e visitante)
```

| Relacionamento | Mapeamento |
|---|---|
| **Time 1:1 Estádio** | `Time` é o dono (`@JoinColumn`, `CascadeType.ALL`); `Estadio` usa `mappedBy` + `@JsonIgnore` |
| **Time 1:N Jogador** | `@OneToMany(mappedBy = "time", cascade = ALL)` no Time; `@ManyToOne` no Jogador |
| **Campeonato N:N Time** | `@ManyToMany` com tabela de junção `tb_campeonato_time` (dono: Campeonato) |
| **Partida** | entidade associativa: `@ManyToOne` para mandante, visitante e campeonato, mais o placar |

**Enums:** `PosicaoJogador` (Jogador) e `StatusPartida` (Partida), gravados como texto (`EnumType.STRING`).

## Endpoints

Todas as listagens são **paginadas** (`page`, `size`, `sort`, ex.: `?page=0&size=10&sort=nome,asc`).

| Recurso | Endpoints | Consultas personalizadas |
|---|---|---|
| **/times** | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `GET /{id}/jogadores`, `PUT /{id}/estadio/{estadioId}` | `GET /buscar-por-nome?nome=` |
| **/estadios** | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | `GET /buscar-por-capacidade?capacidadeMinima=`, `GET /buscar-por-nome?nome=` |
| **/jogadores** | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | `GET /buscar-por-posicao?posicao=`, `GET /buscar-por-nome?nome=` |
| **/campeonatos** | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/times/{timeId}`, `DELETE /{id}/times/{timeId}` | `GET /buscar-por-ano?ano=`, `GET /buscar-por-nome?nome=` |
| **/partidas** | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | `GET /buscar-por-status?status=` |

### Códigos de status

`200` OK · `201` Created (com `Location`) · `204` No Content · `400` dados inválidos · `401` chave de API ausente/inválida/revogada (v2) · `404` não encontrado · `409` conflito (regra de negócio ou idempotência em processamento) · `422` chave de idempotência reutilizada com outro corpo/rota (v2) · `429` limite de requisições excedido (v2).

### Regras de negócio

- O time é cadastrado junto com seu estádio (cascata); remover o time remove estádio e jogadores.
- Um time **com partidas** não pode ser removido (`409`); um campeonato com partidas também não.
- Um estádio que pertence a um time não pode ser removido (`409`).
- Em uma partida, mandante e visitante precisam ser times **diferentes** e **participar do campeonato** informado.
- Referências a outras entidades são enviadas só com o id, ex.: `"time": { "id": 1 }`.

## HATEOAS (REST nível 3)

As entidades JPA são "puras". Os links são criados por classes `*ModelAssembler` na camada web, e as listagens usam `PagedModel` (formato HAL). Cada recurso traz `_links` com `self`, `update`, `delete`, o link da coleção e links para recursos relacionados (ex.: `jogadores`, `estadio`, `mandante`, `visitante`).

Exemplo de resposta de `GET /times/1`:

```json
{
  "id": 1,
  "nome": "Palmeiras",
  "sigla": "PAL",
  "dataFundacao": "1914-08-26",
  "estadio": { "id": 1, "nome": "Allianz Parque", "capacidade": 43713 },
  "_links": {
    "self":     { "href": "http://localhost:8080/times/1" },
    "update":   { "href": "http://localhost:8080/times/1" },
    "delete":   { "href": "http://localhost:8080/times/1" },
    "jogadores":{ "href": "http://localhost:8080/times/1/jogadores" },
    "times":    { "href": "http://localhost:8080/times" },
    "estadio":  { "href": "http://localhost:8080/estadios/1" }
  }
}
```

## Tratamento de erros

O `GlobalExceptionHandler` (`@RestControllerAdvice`) devolve sempre o mesmo formato:

```json
{
  "timestamp": "2026-10-15T20:30:00",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Dados inválidos. Verifique os campos informados.",
  "campos": { "sigla": "A sigla deve conter exatamente 3 letras maiúsculas. Números não são permitidos." }
}
```

## Parte 2: publicação e segurança

A API está publicada no **Render** (Docker) e ganhou cinco recursos, todos **ativos na versão 2** (`X-API-Version: 2`, que é o padrão quando o header é omitido). A **versão 1** (`X-API-Version: 1`) é a API original da Parte 1, sem nenhum deles, para mostrar a evolução do projeto.

| Recurso | O que faz | Onde está |
|---|---|---|
| **Chave de API** | `POST`/`PUT`/`DELETE` e todo `/api-keys` exigem o header `X-API-Key`; `GET` é público | `filter/ApiKeyFilter`, `model/ApiKey` |
| **Rate limit** | 60 requisições por minuto por IP (configurável); excedeu → `429` | `filter/RateLimitFilter` |
| **CORS** | só origens permitidas podem chamar a API pelo navegador | `config/CorsConfig`, `filter/CorsPorVersaoFilter` |
| **Idempotência** | `X-Idempotency-Key` em `POST` evita criar o mesmo recurso duas vezes | `filter/IdempotencyFilter`, `model/IdempotencyRecord` |
| **Versionamento** | header `X-API-Version` (`1` ou `2`; outro valor → `400`) | `filter/ApiVersion`, `filter/ApiVersionFilter` |

> Os filtros rodam **antes** do Spring MVC, por isso o `@RestControllerAdvice` não os alcança: cada filtro escreve o seu próprio JSON de erro no mesmo formato `ApiError`.

### Variáveis de ambiente

| Variável | Para que serve | Padrão |
|---|---|---|
| `PORT` | porta HTTP (o Render define) | `8080` |
| `INITIAL_API_KEY` | chave inicial, criada ao iniciar (serve para criar as demais) | chave de desenvolvimento local |
| `CORS_ALLOWED_ORIGINS` | origens permitidas, separadas por vírgula (nunca `*`) | `http://localhost:3000,5173,5500,8080` e `http://127.0.0.1:5500` |
| `RATE_LIMIT_PER_MINUTE` | requisições por minuto por IP | `60` |
| `SPRING_H2_CONSOLE_ENABLED` | liga/desliga o console do H2 (use `false` em produção) | `true` |

### Publicação (Render)

`api-futebol/Dockerfile` em duas etapas (JDK 17 compila com o `mvnw`; JRE 17 executa). No Render: serviço **Docker**, *Dockerfile Path* `./api-futebol/Dockerfile`, *Docker Build Context Directory* `./api-futebol`, *Root Directory* vazio e as variáveis acima em **Environment**. `server.forward-headers-strategy=framework` faz os links HATEOAS e o Swagger saírem em `https` atrás do proxy.

### Exemplos com curl

```bash
API=https://api-futebol-lfxw.onrender.com     # ou http://localhost:8080
KEY=sua-chave-inicial                          # nunca coloque a chave no código nem no GitHub
```

**1. Chave de API (v2)**

```bash
# GET é público
curl -i $API/times

# Escrever sem chave -> 401
curl -i -X POST $API/estadios -H "Content-Type: application/json" -d '{"nome":"Maracana","capacidade":78000}'

# Criar uma chave nova (a chave completa só aparece nesta resposta)
curl -X POST $API/api-keys -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d '{"nome":"frontend"}'

# Listar chaves (sem hash) e revogar uma (204)
curl $API/api-keys -H "X-API-Key: $KEY"
curl -i -X DELETE $API/api-keys/2 -H "X-API-Key: $KEY"
```

**2. Idempotência (v2): repetir o POST não cria de novo**

```bash
# 1ª vez -> 201
curl -i -X POST $API/estadios -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: pedido-001" -d '{"nome":"Maracana","capacidade":78000}'

# Mesma chave e mesmo corpo -> 201 com o MESMO id e o header Idempotent-Replayed: true
curl -i -X POST $API/estadios -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: pedido-001" -d '{"nome":"Maracana","capacidade":78000}'

# Mesma chave com corpo diferente -> 422
curl -i -X POST $API/estadios -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: pedido-001" -d '{"nome":"Maracana","capacidade":99999}'
```

Só `POST` em `/times`, `/estadios`, `/jogadores`, `/campeonatos` e `/partidas` (não em `/api-keys`), e só respostas `2xx` são guardadas. Mesma chave ainda em processamento → `409`.

**3. Versões**

```bash
# v1 (API original): escreve sem chave -> 201
curl -i -X POST $API/estadios -H "X-API-Version: 1" -H "Content-Type: application/json" -d '{"nome":"Morumbi","capacidade":66000}'

# v2 (explícita ou sem header): exige chave -> 401 sem ela
curl -i -X POST $API/estadios -H "X-API-Version: 2" -H "Content-Type: application/json" -d '{"nome":"Morumbi","capacidade":66000}'

# Versão inválida -> 400
curl -i $API/times -H "X-API-Version: 3"
```

A resposta devolve o header `X-API-Version` com a versão usada.

**4. Rate limit (v2)**

```bash
# Toda resposta traz X-RateLimit-Limit, X-RateLimit-Remaining e X-RateLimit-Reset (segundos)
curl -i $API/times

# Ao passar do limite -> 429 + Retry-After (segundos)
for i in $(seq 1 70); do curl -s -o /dev/null -w "%{http_code} " $API/times; done
```

**5. CORS (v2)**

```bash
# Origem permitida -> 200 com Access-Control-Allow-Origin
curl -i -X OPTIONS $API/estadios -H "Origin: http://localhost:5500" \
  -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: x-api-version,x-api-key,content-type"

# Origem não permitida -> 403, sem Access-Control-Allow-Origin (o navegador bloqueia)
curl -i $API/times -H "Origin: http://site-qualquer.com"
```

O CORS só atua no navegador (curl e Postman não são bloqueados). Os headers `Location`, `Retry-After`, `X-RateLimit-*` e `Idempotent-Replayed` são expostos ao JavaScript.

### Swagger

A página `/` tem um menu **Versão da API** no topo (padrão **V2**): cada versão carrega a sua própria documentação (`/v3/api-docs/v1` e `/v3/api-docs/v2`). A V1 mostra só a API original; a V2 acrescenta o grupo **API Keys**, o botão **Authorize** (esquema `X-API-Key`), o campo `X-Idempotency-Key`, os headers de rate limit e as respostas `401`, `409`, `422` e `429`.

## Estrutura do projeto

```
src/main/java/com/campeonato/api_futebol
├── model/        entidades JPA e enums (inclui ApiKey e IdempotencyRecord)
├── repository/   interfaces Spring Data (consultas personalizadas)
├── assembler/    criação dos links HATEOAS
├── controller/   endpoints REST + anotações do Swagger
├── exception/    tratamento global de erros
├── filter/       filtros da Parte 2: versão, rate limit, API Key, idempotência e CORS por versão
└── config/       OpenAPI (documentação por versão), CORS e criação da chave inicial
src/main/resources/static/   página customizada do Swagger (HTML + CSS)
postman/                      coleção do Postman
```

## Swagger customizado

O título, a descrição detalhada e as tags ficam na classe `OpenApiConfig`. O visual é customizado por `static/swagger-custom.html` + `static/swagger-custom.css`, uma página que carrega o Swagger UI e aplica o CSS do projeto por cima. A rota `/` redireciona para ela.

## Coleção do Postman

Importe `postman/api-futebol.postman_collection.json` e execute as pastas **na ordem** (1 a 6). Os ids criados são guardados em variáveis da coleção e há testes automáticos de status.

**Parte 2:** antes de usar, preencha a variável **`apiKey`** da coleção (sua chave inicial; a coleção a envia em `X-API-Key` automaticamente) e, para testar a publicação, troque **`baseUrl`** pelo endereço do Render. A variável **`apiVersion`** (`1` ou `2`, padrão `2`) é enviada em `X-API-Version`. A pasta **7** cobre chave de API, idempotência (variável `idempotencyKey`, gerada a cada execução) e versões. Rode a pasta 7 **separada**: a coleção inteira passa de 60 requisições por minuto, e o rate limit responderia `429`.

## Decisões de arquitetura

- **Sem camada Service:** o projeto usa Controller → Repository → Model, e a lógica de negócio (regras e validações) fica nos controllers.
- **Injeção por construtor** em vez de `@Autowired` em campo.
- **Entidades separadas de HATEOAS:** as entidades não estendem `RepresentationModel`.
