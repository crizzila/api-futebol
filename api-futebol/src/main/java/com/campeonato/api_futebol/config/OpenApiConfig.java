package com.campeonato.api_futebol.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Configuração da documentação (Swagger/OpenAPI): título, descrição, tags e pagina customizada.
 *
 * Há DUAS documentações, uma por versão da API (o menu V1/V2 da página escolhe qual carregar):
 *  - /v3/api-docs/v1: a API original (Parte 1), sem nada de chave de API, rate limit, idempotência ou versionamento.
 *  - /v3/api-docs/v2: tudo da v1 + API Keys, cadeado/Authorize, X-Idempotency-Key e respostas 401/409/422/429.
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    private static final String ESQUEMA_API_KEY = "ApiKeyAuth";

    private static final List<String> RECURSOS = List.of("/times", "/estadios", "/jogadores", "/campeonatos", "/partidas");

    /**
     * Texto único da descrição. As linhas que começam com "§V2§" só existem na v2; na v1 elas são removidas
     * (ver {@link #descricao(boolean)}), e o que sobra é exatamente a descrição original da Parte 1.
     */
    private static final String DESCRICAO = """
            ## O que é esta API?
            **API RESTful para gerenciamento de um Campeonato de Futebol.** Ela permite cadastrar e consultar \
            **times, estádios, jogadores, campeonatos e partidas** (5 entidades), mantendo todos os relacionamentos entre eles.

            ## Como funciona
            | Recurso | O que representa | Relacionamento |
            |---|---|---|
            | **Times** | Clubes de futebol | 1:1 com Estádio, 1:N com Jogadores, N:N com Campeonatos |
            | **Estádios** | Casa de cada time | 1:1 com Time |
            | **Jogadores** | Atletas de um time | N:1 com Time |
            | **Campeonatos** | Competições por temporada | N:N com Times |
            | **Partidas** | Jogos entre dois times (mandante x visitante) dentro de um campeonato, com placar | Entidade associativa |

            O **Estádio** é cadastrado junto com o Time (relacionamento 1:1 com cascata) e é salvo e removido em conjunto com ele.

            ## Recursos da API
            - **REST nível 3 (HATEOAS):** cada resposta traz o campo `_links` (formato HAL) com links para o próprio \
            recurso (`self`), para as ações de edição e remoção (`update`, `delete`), para a coleção e para recursos relacionados, permitindo navegar pela API só seguindo os links.
            - **Paginação:** todas as listagens são paginadas. Use `page` (começa em 0), `size` e `sort` (ex.: `sort=nome,asc`).
            - **Consultas personalizadas:** busca de times por nome, estádios por capacidade, jogadores por posição, campeonatos por ano e partidas por status (e também buscas por nome).
            - **Validação (Bean Validation):** os dados enviados em POST e PUT são validados; erros retornam `400` \
            com a mensagem de cada campo inválido.
            - **Banco de dados:** H2 em memória (os dados são apagados quando a aplicação é reiniciada).

            ## Códigos de resposta
            | Código | Significado |
            |---|---|
            | `200 OK` | Consulta ou atualização realizada com sucesso |
            | `201 Created` | Recurso criado (o cabeçalho `Location` aponta para ele) |
            | `204 No Content` | Recurso removido com sucesso |
            | `400 Bad Request` | Dados inválidos (validação, JSON malformado, ordenação inexistente) |
            §V2§| `401 Unauthorized` | Chave de API ausente, inválida ou revogada (header `X-API-Key`) |
            | `404 Not Found` | Recurso não encontrado |
            | `409 Conflict` | Operação bloqueada por regra de negócio (ex.: time que já tem partidas) |
            §V2§| `422 Unprocessable Entity` | `X-Idempotency-Key` reutilizada com outro corpo ou outra rota |
            §V2§| `429 Too Many Requests` | Limite de requisições por minuto excedido (veja o header `Retry-After`) |
            §V2§
            §V2§## Versão 2: o que foi acrescentado
            §V2§Esta é a **v2** da API (`X-API-Version: 2`, que também é o padrão quando o header é omitido). Em relação à v1, ela adiciona:
            §V2§- **Chave de API:** todo **GET é público**; **POST, PUT e DELETE** e todo o grupo **API Keys** exigem o header `X-API-Key`. Clique em **Authorize**, cole sua chave e use os endpoints protegidos (cadeado).
            §V2§- **Rate limit:** limite de requisições por minuto por IP (`429` + `Retry-After`; headers `X-RateLimit-*`).
            §V2§- **CORS:** só as origens permitidas podem chamar a API pelo navegador.
            §V2§- **Idempotência:** o header `X-Idempotency-Key` em POST evita criar o mesmo recurso duas vezes.
            §V2§- **Versionamento:** header `X-API-Version` (`1` ou `2`; outro valor retorna `400`).

            ## Por onde começar
            §V2§0. (Só para escrever) clique em **Authorize** e informe sua chave de API.
            1. Cadastre um **Time** (`POST /times`) já com seu estádio. Cadastre um segundo time.
            2. Cadastre **Jogadores** informando o time: `"time": { "id": 1 }`.
            3. Cadastre um **Campeonato** e adicione os times com `POST /campeonatos/{id}/times/{timeId}`.
            4. Registre **Partidas** entre dois times do campeonato e atualize o placar com `PUT /partidas/{id}`.
            """;

    private static String descricao(boolean v2) {
        StringBuilder texto = new StringBuilder();
        for (String linha : DESCRICAO.split("\n", -1)) {
            if (linha.startsWith("§V2§")) {
                if (v2) {
                    texto.append(linha.substring(4)).append('\n');
                }
            } else {
                texto.append(linha).append('\n');
            }
        }
        return texto.toString();
    }

    /** Base comum às duas versões: título, contato, licença e as 5 tags originais. */
    @Bean
    public OpenAPI apiFutebolOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Campeonato de Futebol")
                        .version("1.0.0")
                        .description(descricao(false))
                        .contact(new Contact()
                                .name("Cristian Gonçalves DEV")
                                .url("https://github.com/crizzila/api-futebol"))
                        .license(new License().name("Projeto acadêmico")))
                .tags(List.of(
                        new Tag().name("Times").description("Clubes de futebol e seus estádios (1:1) e jogadores (1:N)"),
                        new Tag().name("Estádios").description("Estádios sede dos times (1:1 com Time)"),
                        new Tag().name("Jogadores").description("Atletas vinculados a um time"),
                        new Tag().name("Campeonatos").description("Competições e os times participantes (N:N)"),
                        new Tag().name("Partidas").description("Jogos entre dois times de um campeonato, com placar e status")));
    }

    /** Documentação da v1: só os 5 recursos originais, sem nada acrescentado depois (é o texto/estrutura da Parte 1). */
    @Bean
    public GroupedOpenApi grupoV1() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/times/**", "/estadios/**", "/jogadores/**", "/campeonatos/**", "/partidas/**")
                .addOpenApiCustomizer(ordenacaoPadraoCustomizer())
                .addOpenApiCustomizer(tagsSemDuplicatasCustomizer())
                .build();
    }

    /** Documentação da v2: tudo (inclui /api-keys) com as proteções documentadas. */
    @Bean
    public GroupedOpenApi grupoV2() {
        return GroupedOpenApi.builder()
                .group("v2")
                .pathsToMatch("/**")
                .addOpenApiCustomizer(ordenacaoPadraoCustomizer())
                .addOpenApiCustomizer(descricaoEEsquemaV2())
                .addOpenApiCustomizer(segurancaCustomizer())
                .addOpenApiCustomizer(versaoCustomizer())
                .addOpenApiCustomizer(idempotenciaCustomizer())
                .addOpenApiCustomizer(limiteRequisicoesCustomizer())
                .addOpenApiCustomizer(tagsSemDuplicatasCustomizer())
                .build();
    }

    /** O springdoc junta as tags declaradas aqui com as dos controllers e repete nomes: mantém só a primeira de cada. */
    @Bean
    public OpenApiCustomizer tagsSemDuplicatasCustomizer() {
        return openApi -> {
            if (openApi.getTags() == null) {
                return;
            }
            java.util.Set<String> vistos = new java.util.HashSet<>();
            openApi.setTags(openApi.getTags().stream().filter(t -> vistos.add(t.getName())).toList());
        };
    }

    /**
     * O Swagger UI preenche o campo "sort" com o texto "string" por padrão, o que causava erro 500.
     * Aqui trocamos o exemplo por um valor válido ("id,asc"). Vale para todas as documentações.
     */
    @Bean
    public OpenApiCustomizer ordenacaoPadraoCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operacao -> {
                        if (operacao.getParameters() == null) {
                            return;
                        }
                        operacao.getParameters().stream()
                                .filter(parametro -> "sort".equals(parametro.getName()))
                                .forEach(parametro -> parametro.setExample(List.of("id,asc")));
                    }));
        };
    }

    // ---------- Customizações que existem SÓ na documentação da v2 (não são @Bean para não valerem na v1) ----------

    /** Descrição completa, esquema de segurança (botão Authorize) e a tag "API Keys". */
    private OpenApiCustomizer descricaoEEsquemaV2() {
        return openApi -> {
            openApi.getInfo().setDescription(descricao(true));
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            // Botão "Authorize": a chave é enviada no header X-API-Key
            openApi.getComponents().addSecuritySchemes(ESQUEMA_API_KEY, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-API-Key")
                    .description("Chave de API. Necessária em POST, PUT e DELETE e em todo o grupo /api-keys. GETs são públicos."));
            openApi.getTags().add(0, new Tag().name("API Keys")
                    .description("Gerenciamento de chaves de acesso (exige X-API-Key)"));
        };
    }

    /**
     * Marca com cadeado e documenta a resposta 401 só nos endpoints protegidos:
     * tudo em /api-keys e qualquer método que não seja GET (espelha a regra do ApiKeyFilter).
     */
    private OpenApiCustomizer segurancaCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((caminho, pathItem) ->
                    pathItem.readOperationsMap().forEach((metodo, operacao) -> {
                        boolean protegido = caminho.startsWith("/api-keys")
                                || metodo != PathItem.HttpMethod.GET;
                        if (!protegido) {
                            return;
                        }
                        operacao.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_API_KEY));
                        operacao.getResponses().addApiResponse("401", respostaErro(
                                "Chave de API ausente, inválida ou revogada"));
                    }));
        };
    }

    /**
     * Documenta o header X-API-Version em todas as operações dos 5 recursos. Na página, quem escolhe a versão
     * é o menu do topo (por isso o campo individual fica escondido pelo CSS), mas o header segue documentado aqui.
     */
    private OpenApiCustomizer versaoCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((caminho, pathItem) -> {
                if (!ehRecurso(caminho)) {
                    return;
                }
                pathItem.readOperations().forEach(operacao -> {
                    StringSchema esquema = new StringSchema();
                    esquema.setEnum(List.of("1", "2"));
                    esquema.setDefault("2");
                    operacao.addParametersItem(new HeaderParameter()
                            .name("X-API-Version")
                            .required(false)
                            .description("Versão da API. **1** = original (sem chave de API, rate limit, CORS e "
                                    + "idempotência). **2** = com todas as proteções. Sem o header vale a versão 2. "
                                    + "Outro valor retorna 400. Nesta página, a versão é escolhida no menu "
                                    + "\"Versão da API\" do topo.")
                            .schema(esquema));
                    operacao.getResponses().addApiResponse("400",
                            respostaErro("Dados inválidos ou valor inválido em X-API-Version"));
                });
            });
        };
    }

    /**
     * Documenta a idempotência: nos POST dos 5 recursos, adiciona o header opcional X-Idempotency-Key
     * (campo aparece no Swagger para ser preenchido) e as respostas 409 e 422.
     */
    private OpenApiCustomizer idempotenciaCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((caminho, pathItem) -> {
                if (!ehRecurso(caminho) || pathItem.getPost() == null) {
                    return;
                }
                var operacao = pathItem.getPost();
                operacao.addParametersItem(new HeaderParameter()
                        .name("X-Idempotency-Key")
                        .required(false)
                        .description("Opcional. Repetir o POST com a mesma chave e o mesmo corpo devolve a resposta "
                                + "guardada (header `Idempotent-Replayed: true`) sem criar de novo.")
                        .schema(new StringSchema().example("abc-123")));
                operacao.getResponses().addApiResponse("409", respostaErro(
                        "Já existe uma requisição em processamento com esta X-Idempotency-Key"));
                operacao.getResponses().addApiResponse("422", respostaErro(
                        "A X-Idempotency-Key já foi usada com outro corpo ou outra rota"));
            });
        };
    }

    /**
     * Documenta o rate limit: resposta 429 (com Retry-After e X-RateLimit-*) e os headers X-RateLimit-*
     * nas respostas de sucesso. Também documenta o header Idempotent-Replayed no 201 dos POST.
     */
    private OpenApiCustomizer limiteRequisicoesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((caminho, pathItem) -> {
                boolean recurso = ehRecurso(caminho);
                if (!recurso && !caminho.startsWith("/api-keys")) {
                    return;
                }
                pathItem.readOperationsMap().forEach((metodo, operacao) -> {
                    // Respostas de sucesso (2xx): mostram o saldo do limite do cliente
                    operacao.getResponses().forEach((codigo, resposta) -> {
                        if (codigo.startsWith("2")) {
                            adicionarHeadersDeLimite(resposta);
                            if (recurso && metodo == PathItem.HttpMethod.POST && codigo.equals("201")) {
                                resposta.addHeaderObject("Idempotent-Replayed", cabecalho(
                                        "`true` quando é a repetição de um POST com a mesma X-Idempotency-Key "
                                                + "(resposta guardada, nada foi criado de novo)", "true"));
                            }
                        }
                    });
                    // 429: limite excedido
                    ApiResponse limite = respostaErro("Limite de requisições por minuto excedido (por IP)");
                    adicionarHeadersDeLimite(limite);
                    limite.addHeaderObject("Retry-After", cabecalho(
                            "Segundos até poder tentar de novo", "42"));
                    operacao.getResponses().addApiResponse("429", limite);
                });
            });
        };
    }

    private static void adicionarHeadersDeLimite(ApiResponse resposta) {
        resposta.addHeaderObject("X-RateLimit-Limit", cabecalho("Máximo de requisições por minuto", "60"));
        resposta.addHeaderObject("X-RateLimit-Remaining", cabecalho("Requisições restantes na janela atual", "59"));
        resposta.addHeaderObject("X-RateLimit-Reset", cabecalho("Segundos até a janela zerar", "60"));
    }

    private static Header cabecalho(String descricao, String exemplo) {
        return new Header().description(descricao).schema(new StringSchema().example(exemplo));
    }

    private static boolean ehRecurso(String caminho) {
        return RECURSOS.stream().anyMatch(r -> caminho.equals(r) || caminho.startsWith(r + "/"));
    }

    // Resposta de erro no formato ApiError (mesmo schema usado pelos controllers)
    private static ApiResponse respostaErro(String descricao) {
        return new ApiResponse()
                .description(descricao)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }

    /** Abrir http://localhost:8080/ leva direto para a documentação customizada. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-custom.html");
    }
}
