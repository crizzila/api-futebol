package com.campeonato.api_futebol.controller;

import com.campeonato.api_futebol.assembler.EstadioModelAssembler;
import com.campeonato.api_futebol.exception.ApiError;
import com.campeonato.api_futebol.model.Estadio;
import com.campeonato.api_futebol.repository.EstadioRepository;
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
@RequestMapping("/estadios")
@Tag(name = "Estádios", description = "Estádios sede dos times (relacionamento 1:1 com Time). Normalmente o estádio é criado junto com o time em `POST /times`; estes endpoints permitem consultar e gerenciar os estádios separadamente.")
public class EstadioController {

    private final EstadioRepository estadioRepository;
    private final EstadioModelAssembler assembler;

    public EstadioController(EstadioRepository estadioRepository, EstadioModelAssembler assembler) {
        this.estadioRepository = estadioRepository;
        this.assembler = assembler;
    }

    @Operation(summary = "Lista todos os estádios (paginado)",
            description = "Retorna uma página de estádios no formato HAL, com links `self`, `update`, `delete` e, quando existir, `time`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de estádios retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação ou ordenação inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Estadio>>> listarTodos(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Estadio> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(estadioRepository.findAll(pageable), assembler));
    }

    @Operation(summary = "Busca um estádio pelo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estádio encontrado"),
            @ApiResponse(responseCode = "404", description = "Estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Estadio>> buscarPorId(
            @Parameter(description = "ID do estádio", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(buscarOuFalhar(id)));
    }

    @Operation(summary = "Busca estádios por capacidade mínima (consulta personalizada)",
            description = "Retorna os estádios com capacidade **maior ou igual** ao valor informado. Resultado paginado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Busca realizada (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Capacidade inválida ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/buscar-por-capacidade")
    public ResponseEntity<PagedModel<EntityModel<Estadio>>> buscarPorCapacidade(
            @Parameter(description = "Capacidade mínima de público", example = "40000") @RequestParam Integer capacidadeMinima,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Estadio> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(
                estadioRepository.findByCapacidadeGreaterThanEqual(capacidadeMinima, pageable), assembler));
    }

    @Operation(summary = "Busca estádios pelo nome (consulta personalizada)",
            description = "Retorna os estádios cujo nome contém o texto informado, ignorando maiúsculas e minúsculas. Resultado paginado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Busca realizada (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro `nome` ausente ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/buscar-por-nome")
    public ResponseEntity<PagedModel<EntityModel<Estadio>>> buscarPorNome(
            @Parameter(description = "Parte do nome do estádio", example = "allianz") @RequestParam String nome,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler<Estadio> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(
                estadioRepository.findByNomeContainingIgnoreCase(nome, pageable), assembler));
    }

    @Operation(summary = "Cadastra um novo estádio",
            description = "Cria um estádio avulso. Depois ele pode ser vinculado a um time com `PUT /times/{id}/estadio/{estadioId}`.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "Estádio", value = """
                    {
                      "nome": "Maracanã",
                      "capacidade": 78000
                    }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estádio criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (Bean Validation)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<Estadio>> cadastrar(@RequestBody @Valid Estadio estadio) {
        estadio.setId(null);
        EntityModel<Estadio> modelo = assembler.toModel(estadioRepository.save(estadio));
        return ResponseEntity
                .created(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(modelo);
    }

    @Operation(summary = "Atualiza um estádio existente",
            description = "Atualiza nome e capacidade. O vínculo com o time não é alterado aqui.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estádio atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (Bean Validation)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Estadio>> atualizar(
            @Parameter(description = "ID do estádio", example = "1") @PathVariable Long id,
            @RequestBody @Valid Estadio dados) {

        Estadio existente = buscarOuFalhar(id);
        existente.setNome(dados.getNome());
        existente.setCapacidade(dados.getCapacidade());
        return ResponseEntity.ok(assembler.toModel(estadioRepository.save(existente)));
    }

    @Operation(summary = "Remove um estádio",
            description = "Só é possível remover estádios que **não** pertencem a nenhum time. Para remover o estádio de um time, remova o time (`DELETE /times/{id}`).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Estádio removido com sucesso"),
            @ApiResponse(responseCode = "404", description = "Estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Estádio pertence a um time e não pode ser removido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID do estádio", example = "1") @PathVariable Long id) {

        Estadio estadio = buscarOuFalhar(id);
        if (estadio.getTime() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O estádio pertence ao time '" + estadio.getTime().getNome() + "' e não pode ser removido.");
        }
        estadioRepository.delete(estadio);
        return ResponseEntity.noContent().build();
    }

    private Estadio buscarOuFalhar(Long id) {
        return estadioRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Estádio com id " + id + " não encontrado."));
    }
}
