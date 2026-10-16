package com.campeonato.api_futebol.controller;

import com.campeonato.api_futebol.assembler.JogadorModelAssembler;
import com.campeonato.api_futebol.exception.ApiError;
import com.campeonato.api_futebol.model.Jogador;
import com.campeonato.api_futebol.model.PosicaoJogador;
import com.campeonato.api_futebol.model.Time;
import com.campeonato.api_futebol.repository.JogadorRepository;
import com.campeonato.api_futebol.repository.TimeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/jogadores")
@Tag(name = "Jogadores", description = "Atletas vinculados a um time (relacionamento N:1 com Time).")
public class JogadorController {

    private final JogadorRepository jogadorRepository;
    private final TimeRepository timeRepository;
    private final JogadorModelAssembler assembler;

    public JogadorController(JogadorRepository jogadorRepository,
                             TimeRepository timeRepository,
                             JogadorModelAssembler assembler) {
        this.jogadorRepository = jogadorRepository;
        this.timeRepository = timeRepository;
        this.assembler = assembler;
    }

    @Operation(summary = "Lista todos os jogadores (paginado)",
            description = "Retorna uma página de jogadores no formato HAL, com links `self` e link para o time de cada jogador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de jogadores retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação ou ordenação inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Jogador>>> listarTodos(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Jogador> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(jogadorRepository.findAll(pageable), assembler));
    }

    @Operation(summary = "Busca um jogador pelo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jogador encontrado"),
            @ApiResponse(responseCode = "404", description = "Jogador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Jogador>> buscarPorId(
            @Parameter(description = "ID do jogador", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(buscarOuFalhar(id)));
    }

    @Operation(summary = "Busca jogadores por posição (consulta personalizada)",
            description = "Retorna os jogadores que atuam na posição informada. Resultado paginado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Busca realizada (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Posição inexistente ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/buscar-por-posicao")
    public ResponseEntity<PagedModel<EntityModel<Jogador>>> buscarPorPosicao(
            @Parameter(description = "Posição do jogador", example = "ATACANTE") @RequestParam PosicaoJogador posicao,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Jogador> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(jogadorRepository.findByPosicao(posicao, pageable), assembler));
    }

    @Operation(summary = "Busca jogadores pelo nome (consulta personalizada)",
            description = "Retorna os jogadores cujo nome contém o texto informado, ignorando maiúsculas e minúsculas. Resultado paginado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Busca realizada (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro `nome` ausente ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/buscar-por-nome")
    public ResponseEntity<PagedModel<EntityModel<Jogador>>> buscarPorNome(
            @Parameter(description = "Parte do nome do jogador", example = "veiga") @RequestParam String nome,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Jogador> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(jogadorRepository.findByNomeContainingIgnoreCase(nome, pageable), assembler));
    }

    @Operation(summary = "Cadastra um novo jogador",
            description = "O jogador precisa pertencer a um time já existente. Informe apenas o id do time.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "Jogador", value = """
                    {
                      "nome": "Raphael Veiga",
                      "posicao": "MEIA",
                      "time": { "id": 1 }
                    }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Jogador criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou time informado não existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<Jogador>> cadastrar(@RequestBody @Valid Jogador jogador) {
        jogador.setId(null);
        jogador.setTime(resolverTime(jogador));

        EntityModel<Jogador> modelo = assembler.toModel(jogadorRepository.save(jogador));
        return ResponseEntity
                .created(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(modelo);
    }

    @Operation(summary = "Atualiza um jogador existente",
            description = "Atualiza nome, posição e time do jogador (permite transferência entre times).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jogador atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou time informado não existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Jogador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Jogador>> atualizar(
            @Parameter(description = "ID do jogador", example = "1") @PathVariable Long id,
            @RequestBody @Valid Jogador dados) {

        Jogador existente = buscarOuFalhar(id);
        existente.setNome(dados.getNome());
        existente.setPosicao(dados.getPosicao());
        existente.setTime(resolverTime(dados));

        return ResponseEntity.ok(assembler.toModel(jogadorRepository.save(existente)));
    }

    @Operation(summary = "Remove um jogador")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Jogador removido com sucesso"),
            @ApiResponse(responseCode = "404", description = "Jogador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID do jogador", example = "1") @PathVariable Long id) {
        jogadorRepository.delete(buscarOuFalhar(id));
        return ResponseEntity.noContent().build();
    }

    private Jogador buscarOuFalhar(Long id) {
        return jogadorRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Jogador com id " + id + " não encontrado."));
    }

    // O JSON traz só { "id": X } no campo time; aqui buscamos o time real no banco
    private Time resolverTime(Jogador jogador) {
        if (jogador.getTime() == null || jogador.getTime().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe o time do jogador no formato: \"time\": { \"id\": 1 }");
        }
        Long timeId = jogador.getTime().getId();
        return timeRepository.findById(timeId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "O time com id " + timeId + " não existe."));
    }
}
