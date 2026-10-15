package com.campeonato.api_futebol.controller;

import com.campeonato.api_futebol.model.Time;
import com.campeonato.api_futebol.repository.TimeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/times")
@Tag(name = "Times", description = "Endpoints para gerenciamento de clubes de futebol")
public class TimeController {

    @Autowired
    private TimeRepository timeRepository;

    // 1 - LISTAR TODOS COM PAGINAÇÃO E HATEOAS SEGURO
    @Operation(summary = "Lista todos os times de forma paginada")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Time>>> listarTodos(
            Pageable pageable,
            PagedResourcesAssembler<Time> assembler) {

        Page<Time> times = timeRepository.findAll(pageable);

        PagedModel<EntityModel<Time>> pagedModel = assembler.toModel(times, time -> {
            EntityModel<Time> resource = EntityModel.of(time);
            resource.add(WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(TimeController.class).buscarPorId(time.getId())).withSelfRel());
            return resource;
        });

        return ResponseEntity.status(HttpStatus.OK).body(pagedModel);
    }

    // 2. BUSCAR POR ID COM HATEOAS
    @Operation(summary = "Busca um time específico pelo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Time encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Time>> buscarPorId(@PathVariable Long id) {
        Optional<Time> timeOpt = timeRepository.findById(id);

        if (timeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Time time = timeOpt.get();

        EntityModel<Time> resource = EntityModel.of(time);
        resource.add(WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(TimeController.class).buscarPorId(id)).withSelfRel());
        resource.add(WebMvcLinkBuilder.linkTo(TimeController.class).withRel("times-cadastrados"));

        return ResponseEntity.status(HttpStatus.OK).body(resource);
    }

    // 3. CONSULTA PERSONALIZADA
    @Operation(summary = "Busca times pelo nome de forma personalizada e paginada")
    @ApiResponse(responseCode = "200", description = "Busca realizada com sucesso")
    @GetMapping("/buscar-por-nome")
    public ResponseEntity<Page<Time>> buscarPorNome(@RequestParam String nome, Pageable pageable) {
        Page<Time> times = timeRepository.findByNomeContainingIgnoreCase(nome, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(times);
    }

    // --- 4. CADASTRAR NOVO TIME (POST com HATEOAS) ---
    @Operation(summary = "Cadastra um novo time no sistema")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Time criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos (Bean Validation)")
    })
    @PostMapping
    public ResponseEntity<EntityModel<Time>> cadastrar(@RequestBody @Valid Time time) {
        Time novoTime = timeRepository.save(time);

        EntityModel<Time> resource = EntityModel.of(novoTime);
        resource.add(WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(TimeController.class).buscarPorId(novoTime.getId())).withSelfRel());
        resource.add(WebMvcLinkBuilder.linkTo(TimeController.class).withRel("times-cadastrados"));

        return ResponseEntity.status(HttpStatus.CREATED).body(resource);
    }

    // --- 5. ATUALIZAR TIME (PUT) ---
    @Operation(summary = "Atualiza os dados de um time existente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Time atualizado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado para atualização"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Time> atualizar(@PathVariable Long id, @RequestBody @Valid Time timeAtualizado) {
        Optional<Time> timeOpt = timeRepository.findById(id);

        if (timeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        timeAtualizado.setId(id);
        Time timeSalvo = timeRepository.save(timeAtualizado);
        return ResponseEntity.status(HttpStatus.OK).body(timeSalvo);
    }

    // --- 6. DELETAR TIME (DELETE) ---
    @Operation(summary = "Deleta um time do sistema pelo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Time deletado com sucesso (No Content)"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Optional<Time> timeOpt = timeRepository.findById(id);

        if (timeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        timeRepository.deleteById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}