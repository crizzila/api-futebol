package com.campeonato.api_futebol.controller;

import com.campeonato.api_futebol.assembler.ApiKeyModelAssembler;
import com.campeonato.api_futebol.exception.ApiError;
import com.campeonato.api_futebol.model.ApiKey;
import com.campeonato.api_futebol.repository.ApiKeyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@RestController
@RequestMapping("/api-keys")
@Tag(name = "API Keys", description = "Gerenciamento das chaves de acesso. **Todos** os endpoints deste grupo exigem uma chave válida no header `X-API-Key`.")
public class ApiKeyController {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Dados que o cliente envia para criar uma chave
    public record ApiKeyRequest(
            @Schema(description = "Nome para identificar quem usa a chave", example = "frontend-github-pages")
            @NotBlank(message = "O nome é obrigatório.")
            @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
            String nome) {
    }

    // Resposta do POST: é a ÚNICA vez em que a chave completa aparece
    public record ApiKeyCriadaResponse(
            Long id, String nome,
            @Schema(description = "Chave completa. Guarde agora: não será mostrada novamente.",
                    example = "fut_3fa9c01d27be84a53fa9c01d27be84a53fa9c01d27be84a53fa9c01d27be84a5")
            String chave,
            String prefixo, boolean ativa, LocalDateTime criadaEm) {
    }

    // Resposta das listagens: sem hash e sem a chave
    public record ApiKeyResponse(Long id, String nome, String prefixo, boolean ativa, LocalDateTime criadaEm) {
        public static ApiKeyResponse de(ApiKey k) {
            return new ApiKeyResponse(k.getId(), k.getNome(), k.getPrefixo(), k.isAtiva(), k.getCriadaEm());
        }
    }

    private final ApiKeyRepository repository;
    private final ApiKeyModelAssembler assembler;

    public ApiKeyController(ApiKeyRepository repository, ApiKeyModelAssembler assembler) {
        this.repository = repository;
        this.assembler = assembler;
    }

    @Operation(summary = "Cria uma nova chave de API",
            description = "Gera uma chave `fut_` + 64 caracteres hexadecimais aleatórios. **A chave completa só aparece nesta resposta**; no banco fica apenas o hash SHA-256.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "Nova chave", value = """
                    {
                      "nome": "frontend-github-pages"
                    }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chave criada (a chave completa vem no campo `chave`)"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<ApiKeyCriadaResponse> criar(@RequestBody @Valid ApiKeyRequest dados) {
        // 32 bytes aleatórios seguros (SecureRandom) em hexadecimal = 64 caracteres
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String chave = "fut_" + HexFormat.of().formatHex(bytes);

        ApiKey salva = repository.save(ApiKey.de(dados.nome().trim(), chave));
        ApiKeyCriadaResponse corpo = new ApiKeyCriadaResponse(
                salva.getId(), salva.getNome(), chave, salva.getPrefixo(), salva.isAtiva(), salva.getCriadaEm());
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri())
                .body(corpo);
    }

    @Operation(summary = "Lista as chaves (paginado)",
            description = "Mostra id, nome, prefixo, se está ativa e quando foi criada. **Nunca** mostra o hash nem a chave completa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de chaves"),
            @ApiResponse(responseCode = "400", description = "Paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<ApiKeyResponse>>> listar(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<ApiKeyResponse> pagedAssembler) {
        var pagina = repository.findAll(pageable).map(ApiKeyResponse::de);
        return ResponseEntity.ok(pagedAssembler.toModel(pagina, assembler));
    }

    @Operation(summary = "Revoga uma chave",
            description = "Marca a chave como inativa (`ativa = false`). Ela deixa de funcionar na hora, mas o registro continua para histórico.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Chave revogada"),
            @ApiResponse(responseCode = "404", description = "Chave não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revogar(
            @Parameter(description = "ID da chave", example = "1") @PathVariable Long id) {
        ApiKey chave = repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Chave com id " + id + " não encontrada."));
        chave.setAtiva(false);
        repository.save(chave);
        return ResponseEntity.noContent().build();
    }
}
