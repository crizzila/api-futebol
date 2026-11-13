package com.campeonato.api_futebol.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Configuração da documentação (Swagger/OpenAPI): título, descrição, tags e pagina customizada.
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    private static final String ESQUEMA_API_KEY = "ApiKeyAuth";

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
            | `401 Unauthorized` | Chave de API ausente, inválida ou revogada (header `X-API-Key`) |
            | `404 Not Found` | Recurso não encontrado |
            | `422 Unprocessable Entity` | `X-Idempotency-Key` reutilizada com outro corpo ou outra rota |
            | `429 Too Many Requests` | Limite de requisições por minuto excedido (veja o header `Retry-After`) |
            | `409 Conflict` | Operação bloqueada por regra de negócio (ex.: time que já tem partidas) |

            ## Versões da API (header `X-API-Version`)
            - **v1** (sem o header, ou `X-API-Version: 1`): comportamento original, **sem** chave de API, rate limit, CORS e idempotência.
            - **v2** (`X-API-Version: 2`): todas as proteções ativas (chave de API, rate limit, CORS e idempotência).
            - O grupo **API Keys** é novo e sempre exige chave. Valor diferente de 1 ou 2 retorna `400`.

            ## Autenticação (API Key, versão 2)
            Na **v2**, todo **GET é público**. **POST, PUT e DELETE** e todo o grupo **API Keys** exigem o header `X-API-Key`. \
            Clique em **Authorize**, cole sua chave e use os endpoints protegidos (cadeado).

            ## Por onde começar
            0. (Só para escrever) clique em **Authorize** e informe sua chave de API.
            1. Cadastre um **Time** (`POST /times`) já com seu estádio. Cadastre um segundo time.
            2. Cadastre **Jogadores** informando o time: `"time": { "id": 1 }`.
            3. Cadastre um **Campeonato** e adicione os times com `POST /campeonatos/{id}/times/{timeId}`.
            4. Registre **Partidas** entre dois times do campeonato e atualize o placar com `PUT /partidas/{id}`.
            """;

    @Bean
    public OpenAPI apiFutebolOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Campeonato de Futebol")
                        .version("1.0.0")
                        .description(DESCRICAO)
                        .contact(new Contact()
                                .name("Cristian Gonçalves DEV")
                                .url("https://github.com/crizzila/api-futebol"))
                        .license(new License().name("Projeto acadêmico")))
                // Botão "Authorize": a chave é enviada no header X-API-Key
                .components(new Components().addSecuritySchemes(ESQUEMA_API_KEY, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("Chave de API. Necessária em POST, PUT e DELETE e em todo o grupo /api-keys. GETs são públicos.")))
                .tags(List.of(
                        new Tag().name("API Keys").description("Gerenciamento de chaves de acesso (exige X-API-Key)"),
                        new Tag().name("Times").description("Clubes de futebol e seus estádios (1:1) e jogadores (1:N)"),
                        new Tag().name("Estádios").description("Estádios sede dos times (1:1 com Time)"),
                        new Tag().name("Jogadores").description("Atletas vinculados a um time"),
                        new Tag().name("Campeonatos").description("Competições e os times participantes (N:N)"),
                        new Tag().name("Partidas").description("Jogos entre dois times de um campeonato, com placar e status")));
    }

    /**
     * O Swagger UI preenche o campo "sort" com o texto "string" por padrão, o que causava erro 500.
     * Aqui trocamos o exemplo por um valor válido ("id,asc").
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

    /**
     * Marca com cadeado e documenta a resposta 401 só nos endpoints protegidos:
     * tudo em /api-keys e qualquer método que não seja GET (espelha a regra do ApiKeyFilter).
     */
    @Bean
    public OpenApiCustomizer segurancaCustomizer() {
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
                        operacao.getResponses().addApiResponse("401", new ApiResponse()
                                .description("Chave de API ausente, inválida ou revogada")
                                .content(new Content().addMediaType("application/json", new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
                    }));
        };
    }

    /**
     * Documenta o header X-API-Version em todas as operações dos 5 recursos. O campo já vem preenchido
     * com "2" (versão protegida); trocar para "1" mostra o comportamento original, sem proteções.
     */
    @Bean
    public OpenApiCustomizer versaoCustomizer() {
        List<String> recursos = List.of("/times", "/estadios", "/jogadores", "/campeonatos", "/partidas");
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((caminho, pathItem) -> {
                boolean versionado = recursos.stream().anyMatch(r -> caminho.equals(r) || caminho.startsWith(r + "/"));
                if (!versionado) {
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
                                    + "idempotência). **2** = com todas as proteções. Sem o header vale a versão 1. "
                                    + "Outro valor retorna 400.")
                            .schema(esquema));
                    operacao.getResponses().addApiResponse("400", new ApiResponse()
                            .description("Dados inválidos ou valor inválido em X-API-Version")
                            .content(new Content().addMediaType("application/json", new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
                });
            });
        };
    }

    /**
     * Documenta a idempotência: nos POST dos 5 recursos, adiciona o header opcional X-Idempotency-Key
     * (campo aparece no Swagger para ser preenchido) e as respostas 409 e 422.
     */
    @Bean
    public OpenApiCustomizer idempotenciaCustomizer() {
        List<String> recursos = List.of("/times", "/estadios", "/jogadores", "/campeonatos", "/partidas");
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((caminho, pathItem) -> {
                boolean recursoIdempotente = recursos.stream()
                        .anyMatch(r -> caminho.equals(r) || caminho.startsWith(r + "/"));
                if (!recursoIdempotente || pathItem.getPost() == null) {
                    return;
                }
                var operacao = pathItem.getPost();
                operacao.addParametersItem(new HeaderParameter()
                        .name("X-Idempotency-Key")
                        .required(false)
                        .description("Opcional. Repetir o POST com a mesma chave e o mesmo corpo devolve a resposta "
                                + "guardada (header `Idempotent-Replayed: true`) sem criar de novo.")
                        .schema(new StringSchema().example("abc-123")));
                operacao.getResponses().addApiResponse("409", new ApiResponse()
                        .description("Já existe uma requisição em processamento com esta X-Idempotency-Key")
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
                operacao.getResponses().addApiResponse("422", new ApiResponse()
                        .description("A X-Idempotency-Key já foi usada com outro corpo ou outra rota")
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
            });
        };
    }

    /** Abrir http://localhost:8080/ leva direto para a documentação customizada. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-custom.html");
    }
}
