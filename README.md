# API Campeonato de Futebol

API RESTful desenvolvida com **Spring Boot** para gerenciar um campeonato de futebol: **times, estádios, jogadores, campeonatos e partidas**.
Projeto final do curso de Desenvolvimento de APIs com Spring Boot (**Parte 1**).

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

`200` OK · `201` Created (com `Location`) · `204` No Content · `400` dados inválidos · `404` não encontrado · `409` conflito (regra de negócio).

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

## Estrutura do projeto

```
src/main/java/com/campeonato/api_futebol
├── model/        entidades JPA e enums
├── repository/   interfaces Spring Data (consultas personalizadas)
├── assembler/    criação dos links HATEOAS
├── controller/   endpoints REST + anotações do Swagger
├── exception/    tratamento global de erros
└── config/       configuração do OpenAPI (título, descrição, tags)
src/main/resources/static/   página customizada do Swagger (HTML + CSS)
postman/                      coleção do Postman
```

## Swagger customizado

O título, a descrição detalhada e as tags ficam na classe `OpenApiConfig`. O visual é customizado por `static/swagger-custom.html` + `static/swagger-custom.css`, uma página que carrega o Swagger UI e aplica o CSS do projeto por cima. A rota `/` redireciona para ela.

## Coleção do Postman

Importe `postman/api-futebol.postman_collection.json` e execute as pastas **na ordem** (1 a 6). Os ids criados são guardados em variáveis da coleção e há testes automáticos de status.

## Decisões de arquitetura

- **Sem camada Service:** o projeto usa Controller → Repository → Model, e a lógica de negócio (regras e validações) fica nos controllers.
- **Injeção por construtor** em vez de `@Autowired` em campo.
- **Entidades separadas de HATEOAS:** as entidades não estendem `RepresentationModel`.
