package com.campeonato.api_futebol.config;

import io.swagger.v3.oas.models.OpenAPI;
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
            | `404 Not Found` | Recurso não encontrado |
            | `409 Conflict` | Operação bloqueada por regra de negócio (ex.: time que já tem partidas) |

            ## Por onde começar
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
                .tags(List.of(
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

    /** Abrir http://localhost:8080/ leva direto para a documentação customizada. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-custom.html");
    }
}
