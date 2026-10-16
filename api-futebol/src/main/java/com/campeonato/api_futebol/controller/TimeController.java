package com.campeonato.api_futebol.controller;

import com.campeonato.api_futebol.assembler.JogadorModelAssembler;
import com.campeonato.api_futebol.assembler.TimeModelAssembler;
import com.campeonato.api_futebol.exception.ApiError;
import com.campeonato.api_futebol.model.Campeonato;
import com.campeonato.api_futebol.model.Estadio;
import com.campeonato.api_futebol.model.Jogador;
import com.campeonato.api_futebol.model.Time;
import com.campeonato.api_futebol.repository.EstadioRepository;
import com.campeonato.api_futebol.repository.JogadorRepository;
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

@RestController
@RequestMapping("/times")
@Tag(name = "Times", description = "Clubes de futebol. Cada time tem um estádio (1:1), vários jogadores (1:N) e participa de vários campeonatos (N:N).")
public class TimeController {

    private final TimeRepository timeRepository;
    private final JogadorRepository jogadorRepository;
    private final EstadioRepository estadioRepository;
    private final PartidaRepository partidaRepository;
    private final TimeModelAssembler assembler;
    private final JogadorModelAssembler jogadorAssembler;

    // Injeção por construtor (melhor prática: dependências obrigatórias e fáceis de testar)
    public TimeController(TimeRepository timeRepository,
                          JogadorRepository jogadorRepository,
                          EstadioRepository estadioRepository,
                          PartidaRepository partidaRepository,
                          TimeModelAssembler assembler,
                          JogadorModelAssembler jogadorAssembler) {
        this.timeRepository = timeRepository;
        this.jogadorRepository = jogadorRepository;
        this.estadioRepository = estadioRepository;
        this.partidaRepository = partidaRepository;
        this.assembler = assembler;
        this.jogadorAssembler = jogadorAssembler;
    }

    // 1. LISTAR (paginado + HATEOAS)
    @Operation(summary = "Lista todos os times (paginado)",
            description = "Retorna uma página de times no formato HAL, com links `self` de cada time e links de navegação entre páginas (`first`, `prev`, `next`, `last`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de times retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação ou ordenação inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Time>>> listarTodos(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Time> pagedAssembler) {

        PagedModel<EntityModel<Time>> pagina = pagedAssembler.toModel(timeRepository.findAll(pageable), assembler);
        return ResponseEntity.ok(pagina);
    }

    // 2. BUSCAR POR ID
    @Operation(summary = "Busca um time pelo ID",
            description = "Retorna o time com seu estádio e links para o próprio time, para seus jogadores e para a lista de times.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Time encontrado"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Time>> buscarPorId(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(buscarOuFalhar(id)));
    }

    // 3. CONSULTA PERSONALIZADA (findByNomeContainingIgnoreCase)
    @Operation(summary = "Busca times pelo nome (consulta personalizada)",
            description = "Retorna os times cujo nome **contém** o texto informado, ignorando maiúsculas e minúsculas. Resultado paginado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Busca realizada (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro `nome` ausente ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/buscar-por-nome")
    public ResponseEntity<PagedModel<EntityModel<Time>>> buscarPorNome(
            @Parameter(description = "Parte do nome do time", example = "palm") @RequestParam String nome,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Time> pagedAssembler) {

        PagedModel<EntityModel<Time>> pagina = pagedAssembler.toModel(
                timeRepository.findByNomeContainingIgnoreCase(nome, pageable), assembler);
        return ResponseEntity.ok(pagina);
    }

    // 4. JOGADORES DE UM TIME (relacionamento 1:N)
    @Operation(summary = "Lista os jogadores de um time (paginado)",
            description = "Navega pelo relacionamento 1:N: retorna os jogadores que pertencem ao time informado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jogadores do time retornados"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}/jogadores")
    public ResponseEntity<PagedModel<EntityModel<Jogador>>> listarJogadores(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Jogador> pagedAssembler) {

        buscarOuFalhar(id);
        PagedModel<EntityModel<Jogador>> pagina = pagedAssembler.toModel(
                jogadorRepository.findByTimeId(id, pageable), jogadorAssembler);
        return ResponseEntity.ok(pagina);
    }

    // 5. CADASTRAR
    @Operation(summary = "Cadastra um novo time",
            description = "Cria o time e, em cascata, o estádio informado dentro do mesmo JSON. Retorna `201 Created` com o cabeçalho `Location` e os links HATEOAS.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "Palmeiras", value = """
                    {
                      "nome": "Palmeiras",
                      "sigla": "PAL",
                      "dataFundacao": "1914-08-26",
                      "estadio": { "nome": "Allianz Parque", "capacidade": 43713 }
                    }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Time criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (Bean Validation)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<Time>> cadastrar(@RequestBody @Valid Time time) {
        // Garante que é um INSERT, mesmo que o cliente envie ids
        time.setId(null);
        if (time.getEstadio() != null) {
            time.getEstadio().setId(null);
        }

        EntityModel<Time> modelo = assembler.toModel(timeRepository.save(time));
        return ResponseEntity
                .created(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(modelo);
    }

    // 6. ATUALIZAR
    @Operation(summary = "Atualiza um time existente",
            description = "Substitui nome, sigla, data de fundação e dados do estádio. Os jogadores e campeonatos do time são preservados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Time atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (Bean Validation)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Time>> atualizar(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id,
            @RequestBody @Valid Time dados) {

        Time existente = buscarOuFalhar(id);
        existente.setNome(dados.getNome());
        existente.setSigla(dados.getSigla());
        existente.setDataFundacao(dados.getDataFundacao());

        // Atualiza o estádio que já existe (em vez de criar um novo e deixar o antigo órfão)
        if (dados.getEstadio() != null) {
            if (existente.getEstadio() == null) {
                dados.getEstadio().setId(null);
                existente.setEstadio(dados.getEstadio());
            } else {
                existente.getEstadio().setNome(dados.getEstadio().getNome());
                existente.getEstadio().setCapacidade(dados.getEstadio().getCapacidade());
            }
        }

        return ResponseEntity.ok(assembler.toModel(timeRepository.save(existente)));
    }

    // 6.1 VINCULAR UM ESTÁDIO JÁ EXISTENTE (1:1)
    @Operation(summary = "Vincula um estádio já cadastrado ao time",
            description = "Troca o estádio sede do time por um estádio existente (`POST /estadios`). Cada estádio pertence a no máximo um time (relacionamento 1:1).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estádio vinculado ao time"),
            @ApiResponse(responseCode = "404", description = "Time ou estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "O estádio já pertence a outro time",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}/estadio/{estadioId}")
    @Transactional
    public ResponseEntity<EntityModel<Time>> vincularEstadio(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id,
            @Parameter(description = "ID do estádio", example = "2") @PathVariable Long estadioId) {

        Time time = buscarOuFalhar(id);
        Estadio estadio = estadioRepository.findById(estadioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Estádio com id " + estadioId + " não encontrado."));

        if (estadio.getTime() != null && !estadio.getTime().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O estádio já pertence a outro time.");
        }
        time.setEstadio(estadio);
        return ResponseEntity.ok(assembler.toModel(timeRepository.save(time)));
    }

    // 7. DELETAR
    @Operation(summary = "Remove um time",
            description = "Remove o time, seu estádio e seus jogadores (cascata) e o desvincula dos campeonatos. "
                    + "Se o time já tiver partidas registradas, a remoção é bloqueada com `409`.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Time removido com sucesso"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Time possui partidas e não pode ser removido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id) {

        Time time = buscarOuFalhar(id);

        if (partidaRepository.existsByMandanteIdOrVisitanteId(id, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O time possui partidas registradas e não pode ser removido.");
        }

        // A tabela de ligação N:N pertence ao Campeonato, então removemos o time de cada campeonato antes
        if (time.getCampeonatos() != null) {
            for (Campeonato campeonato : new ArrayList<>(time.getCampeonatos())) {
                campeonato.getTimes().remove(time);
            }
        }

        timeRepository.delete(time);
        return ResponseEntity.noContent().build();
    }

    private Time buscarOuFalhar(Long id) {
        return timeRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Time com id " + id + " não encontrado."));
    }
}
