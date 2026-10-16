package com.campeonato.api_futebol.controller;

import com.campeonato.api_futebol.assembler.CampeonatoModelAssembler;
import com.campeonato.api_futebol.exception.ApiError;
import com.campeonato.api_futebol.model.Campeonato;
import com.campeonato.api_futebol.model.Time;
import com.campeonato.api_futebol.repository.CampeonatoRepository;
import com.campeonato.api_futebol.repository.PartidaRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/campeonatos")
@Tag(name = "Campeonatos", description = "Competições de futebol. Relacionamento N:N com Times: um campeonato tem vários times e um time pode disputar vários campeonatos.")
public class CampeonatoController {

    private final CampeonatoRepository campeonatoRepository;
    private final TimeRepository timeRepository;
    private final PartidaRepository partidaRepository;
    private final CampeonatoModelAssembler assembler;

    public CampeonatoController(CampeonatoRepository campeonatoRepository,
                                TimeRepository timeRepository,
                                PartidaRepository partidaRepository,
                                CampeonatoModelAssembler assembler) {
        this.campeonatoRepository = campeonatoRepository;
        this.timeRepository = timeRepository;
        this.partidaRepository = partidaRepository;
        this.assembler = assembler;
    }

    @Operation(summary = "Lista todos os campeonatos (paginado)",
            description = "Retorna uma página de campeonatos no formato HAL, com os times participantes de cada um.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de campeonatos retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação ou ordenação inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Campeonato>>> listarTodos(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Campeonato> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(campeonatoRepository.findAll(pageable), assembler));
    }

    @Operation(summary = "Busca um campeonato pelo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Campeonato encontrado"),
            @ApiResponse(responseCode = "404", description = "Campeonato não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Campeonato>> buscarPorId(
            @Parameter(description = "ID do campeonato", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(buscarOuFalhar(id)));
    }

    @Operation(summary = "Busca campeonatos por ano (consulta personalizada)",
            description = "Retorna os campeonatos da temporada informada. Resultado paginado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Busca realizada (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Ano inválido ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/buscar-por-ano")
    public ResponseEntity<PagedModel<EntityModel<Campeonato>>> buscarPorAno(
            @Parameter(description = "Ano da temporada", example = "2026") @RequestParam Integer ano,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Campeonato> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(campeonatoRepository.findByAno(ano, pageable), assembler));
    }

    @Operation(summary = "Busca campeonatos pelo nome (consulta personalizada)",
            description = "Retorna os campeonatos cujo nome contém o texto informado, ignorando maiúsculas e minúsculas. Resultado paginado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Busca realizada (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro `nome` ausente ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/buscar-por-nome")
    public ResponseEntity<PagedModel<EntityModel<Campeonato>>> buscarPorNome(
            @Parameter(description = "Parte do nome do campeonato", example = "paulista") @RequestParam String nome,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Campeonato> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(campeonatoRepository.findByNomeContainingIgnoreCase(nome, pageable), assembler));
    }

    @Operation(summary = "Cadastra um novo campeonato",
            description = "Os times participantes são opcionais na criação: informe só os ids, ou adicione depois em `POST /campeonatos/{id}/times/{timeId}`.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "Campeonato", value = """
                    {
                      "nome": "Campeonato Paulista",
                      "ano": 2026,
                      "times": [ { "id": 1 }, { "id": 2 } ]
                    }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Campeonato criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou time informado não existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<Campeonato>> cadastrar(@RequestBody @Valid Campeonato campeonato) {
        campeonato.setId(null);
        campeonato.setTimes(resolverTimes(campeonato.getTimes()));

        EntityModel<Campeonato> modelo = assembler.toModel(campeonatoRepository.save(campeonato));
        return ResponseEntity
                .created(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(modelo);
    }

    @Operation(summary = "Atualiza nome e ano de um campeonato",
            description = "Os times participantes não são alterados aqui; use os endpoints `/campeonatos/{id}/times/{timeId}`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Campeonato atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Campeonato não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Campeonato>> atualizar(
            @Parameter(description = "ID do campeonato", example = "1") @PathVariable Long id,
            @RequestBody @Valid Campeonato dados) {

        Campeonato existente = buscarOuFalhar(id);
        existente.setNome(dados.getNome());
        existente.setAno(dados.getAno());
        return ResponseEntity.ok(assembler.toModel(campeonatoRepository.save(existente)));
    }

    @Operation(summary = "Remove um campeonato",
            description = "Bloqueado com `409` se o campeonato já tiver partidas registradas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Campeonato removido com sucesso"),
            @ApiResponse(responseCode = "404", description = "Campeonato não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Campeonato possui partidas e não pode ser removido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID do campeonato", example = "1") @PathVariable Long id) {

        Campeonato campeonato = buscarOuFalhar(id);
        if (partidaRepository.existsByCampeonatoId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O campeonato possui partidas registradas e não pode ser removido.");
        }
        campeonatoRepository.delete(campeonato);
        return ResponseEntity.noContent().build();
    }

    // --- RELACIONAMENTO N:N ---

    @Operation(summary = "Adiciona um time ao campeonato",
            description = "Vincula um time existente ao campeonato (relacionamento N:N).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Time adicionado ao campeonato"),
            @ApiResponse(responseCode = "404", description = "Campeonato ou time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "O time já participa deste campeonato",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/times/{timeId}")
    @Transactional
    public ResponseEntity<EntityModel<Campeonato>> adicionarTime(
            @Parameter(description = "ID do campeonato", example = "1") @PathVariable Long id,
            @Parameter(description = "ID do time", example = "1") @PathVariable Long timeId) {

        Campeonato campeonato = buscarOuFalhar(id);
        Time time = timeRepository.findById(timeId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Time com id " + timeId + " não encontrado."));

        if (campeonato.getTimes().stream().anyMatch(t -> t.getId().equals(timeId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O time já participa deste campeonato.");
        }
        campeonato.getTimes().add(time);
        return ResponseEntity.ok(assembler.toModel(campeonatoRepository.save(campeonato)));
    }

    @Operation(summary = "Remove um time do campeonato",
            description = "Desvincula o time do campeonato (o time continua cadastrado).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Time removido do campeonato"),
            @ApiResponse(responseCode = "404", description = "Campeonato não encontrado ou time não participa dele",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}/times/{timeId}")
    @Transactional
    public ResponseEntity<Void> removerTime(
            @Parameter(description = "ID do campeonato", example = "1") @PathVariable Long id,
            @Parameter(description = "ID do time", example = "1") @PathVariable Long timeId) {

        Campeonato campeonato = buscarOuFalhar(id);
        boolean removido = campeonato.getTimes().removeIf(t -> t.getId().equals(timeId));
        if (!removido) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "O time não participa deste campeonato.");
        }
        campeonatoRepository.save(campeonato);
        return ResponseEntity.noContent().build();
    }

    private Campeonato buscarOuFalhar(Long id) {
        return campeonatoRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Campeonato com id " + id + " não encontrado."));
    }

    // Converte os { "id": X } recebidos em times reais do banco (sem duplicados)
    private List<Time> resolverTimes(List<Time> informados) {
        List<Time> resultado = new ArrayList<>();
        if (informados == null) {
            return resultado;
        }
        for (Time informado : informados) {
            if (informado == null || informado.getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada time deve ser informado no formato: { \"id\": 1 }");
            }
            Long timeId = informado.getId();
            Time real = timeRepository.findById(timeId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "O time com id " + timeId + " não existe."));
            if (resultado.stream().noneMatch(t -> t.getId().equals(real.getId()))) {
                resultado.add(real);
            }
        }
        return resultado;
    }
}
