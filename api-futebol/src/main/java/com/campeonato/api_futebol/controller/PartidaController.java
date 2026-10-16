package com.campeonato.api_futebol.controller;

import com.campeonato.api_futebol.assembler.PartidaModelAssembler;
import com.campeonato.api_futebol.exception.ApiError;
import com.campeonato.api_futebol.model.Campeonato;
import com.campeonato.api_futebol.model.Partida;
import com.campeonato.api_futebol.model.StatusPartida;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/partidas")
@Tag(name = "Partidas", description = "Jogos entre um time mandante e um visitante dentro de um campeonato, com placar e status.")
public class PartidaController {

    private final PartidaRepository partidaRepository;
    private final TimeRepository timeRepository;
    private final CampeonatoRepository campeonatoRepository;
    private final PartidaModelAssembler assembler;

    public PartidaController(PartidaRepository partidaRepository,
                             TimeRepository timeRepository,
                             CampeonatoRepository campeonatoRepository,
                             PartidaModelAssembler assembler) {
        this.partidaRepository = partidaRepository;
        this.timeRepository = timeRepository;
        this.campeonatoRepository = campeonatoRepository;
        this.assembler = assembler;
    }

    @Operation(summary = "Lista todas as partidas (paginado)",
            description = "Retorna uma página de partidas no formato HAL, com links para mandante, visitante e campeonato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de partidas retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação ou ordenação inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Partida>>> listarTodas(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Partida> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(partidaRepository.findAll(pageable), assembler));
    }

    @Operation(summary = "Busca uma partida pelo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partida encontrada"),
            @ApiResponse(responseCode = "404", description = "Partida não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Partida>> buscarPorId(
            @Parameter(description = "ID da partida", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(buscarOuFalhar(id)));
    }

    @Operation(summary = "Busca partidas por status (consulta personalizada)",
            description = "Retorna as partidas com a situação informada (AGENDADA, EM_ANDAMENTO, FINALIZADA ou CANCELADA). Resultado paginado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Busca realizada (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Status inexistente ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/buscar-por-status")
    public ResponseEntity<PagedModel<EntityModel<Partida>>> buscarPorStatus(
            @Parameter(description = "Situação da partida", example = "FINALIZADA") @RequestParam StatusPartida status,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Partida> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(partidaRepository.findByStatus(status, pageable), assembler));
    }

    @Operation(summary = "Cadastra uma nova partida",
            description = "Regras: mandante e visitante devem ser times diferentes e **ambos precisam participar do campeonato** informado. "
                    + "Informe apenas os ids de mandante, visitante e campeonato.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "Partida", value = """
                    {
                      "dataHora": "2026-10-20T21:30:00",
                      "status": "AGENDADA",
                      "placarMandante": 0,
                      "placarVisitante": 0,
                      "mandante": { "id": 1 },
                      "visitante": { "id": 2 },
                      "campeonato": { "id": 1 }
                    }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Partida criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, times iguais, time/campeonato inexistente ou time fora do campeonato",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<Partida>> cadastrar(@RequestBody @Valid Partida partida) {
        partida.setId(null);
        preencherReferencias(partida);

        EntityModel<Partida> modelo = assembler.toModel(partidaRepository.save(partida));
        return ResponseEntity
                .created(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(modelo);
    }

    @Operation(summary = "Atualiza uma partida (inclusive o placar e o status)",
            description = "Use este endpoint para registrar o resultado: envie os gols e mude o status para FINALIZADA. As mesmas regras do cadastro valem aqui.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partida atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, times iguais, time/campeonato inexistente ou time fora do campeonato",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Partida não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Partida>> atualizar(
            @Parameter(description = "ID da partida", example = "1") @PathVariable Long id,
            @RequestBody @Valid Partida dados) {

        Partida existente = buscarOuFalhar(id);
        preencherReferencias(dados);

        existente.setDataHora(dados.getDataHora());
        existente.setStatus(dados.getStatus());
        existente.setPlacarMandante(dados.getPlacarMandante());
        existente.setPlacarVisitante(dados.getPlacarVisitante());
        existente.setMandante(dados.getMandante());
        existente.setVisitante(dados.getVisitante());
        existente.setCampeonato(dados.getCampeonato());

        return ResponseEntity.ok(assembler.toModel(partidaRepository.save(existente)));
    }

    @Operation(summary = "Remove uma partida")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Partida removida com sucesso"),
            @ApiResponse(responseCode = "404", description = "Partida não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID da partida", example = "1") @PathVariable Long id) {
        partidaRepository.delete(buscarOuFalhar(id));
        return ResponseEntity.noContent().build();
    }

    private Partida buscarOuFalhar(Long id) {
        return partidaRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida com id " + id + " não encontrada."));
    }

    // Troca os { "id": X } do JSON pelas entidades reais e aplica as regras de negócio da partida
    private void preencherReferencias(Partida partida) {
        Time mandante = buscarTime(partida.getMandante(), "mandante");
        Time visitante = buscarTime(partida.getVisitante(), "visitante");
        Campeonato campeonato = buscarCampeonato(partida.getCampeonato());

        if (mandante.getId().equals(visitante.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mandante e visitante devem ser times diferentes.");
        }
        if (campeonato.getTimes().stream().noneMatch(t -> t.getId().equals(mandante.getId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O time mandante não participa deste campeonato. Adicione-o em POST /campeonatos/{id}/times/{timeId}.");
        }
        if (campeonato.getTimes().stream().noneMatch(t -> t.getId().equals(visitante.getId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O time visitante não participa deste campeonato. Adicione-o em POST /campeonatos/{id}/times/{timeId}.");
        }

        partida.setMandante(mandante);
        partida.setVisitante(visitante);
        partida.setCampeonato(campeonato);

        if (partida.getStatus() == null) {
            partida.setStatus(StatusPartida.AGENDADA);
        }
        if (partida.getPlacarMandante() == null) {
            partida.setPlacarMandante(0);
        }
        if (partida.getPlacarVisitante() == null) {
            partida.setPlacarVisitante(0);
        }
    }

    private Time buscarTime(Time informado, String papel) {
        if (informado == null || informado.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe o time " + papel + " no formato: { \"id\": 1 }");
        }
        Long id = informado.getId();
        return timeRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "O time " + papel + " com id " + id + " não existe."));
    }

    private Campeonato buscarCampeonato(Campeonato informado) {
        if (informado == null || informado.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe o campeonato no formato: { \"id\": 1 }");
        }
        Long id = informado.getId();
        return campeonatoRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "O campeonato com id " + id + " não existe."));
    }
}
