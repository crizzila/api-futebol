package com.campeonato.api_futebol.assembler;

import com.campeonato.api_futebol.controller.EstadioController;
import com.campeonato.api_futebol.controller.TimeController;
import com.campeonato.api_futebol.model.Time;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Converte a entidade Time em EntityModel<Time> (Time + links HATEOAS).
 * Toda a lógica de links fica aqui, e o controller fica limpo.
 * Os links "update" e "delete" apontam para a mesma URL do "self"; o que muda é o método HTTP (PUT / DELETE).
 */
@Component
public class TimeModelAssembler implements RepresentationModelAssembler<Time, EntityModel<Time>> {

    @Override
    public EntityModel<Time> toModel(Time time) {
        EntityModel<Time> modelo = EntityModel.of(time,
                linkTo(methodOn(TimeController.class).buscarPorId(time.getId())).withSelfRel(),
                linkTo(methodOn(TimeController.class).atualizar(time.getId(), null)).withRel("update"),
                linkTo(methodOn(TimeController.class).deletar(time.getId())).withRel("delete"),
                linkTo(TimeController.class).slash(time.getId()).slash("jogadores").withRel("jogadores"),
                linkTo(TimeController.class).withRel("times"));

        if (time.getEstadio() != null && time.getEstadio().getId() != null) {
            modelo.add(linkTo(methodOn(EstadioController.class).buscarPorId(time.getEstadio().getId())).withRel("estadio"));
        }
        return modelo;
    }
}
