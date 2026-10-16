package com.campeonato.api_futebol.assembler;

import com.campeonato.api_futebol.controller.EstadioController;
import com.campeonato.api_futebol.controller.TimeController;
import com.campeonato.api_futebol.model.Estadio;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class EstadioModelAssembler implements RepresentationModelAssembler<Estadio, EntityModel<Estadio>> {

    @Override
    public EntityModel<Estadio> toModel(Estadio estadio) {
        EntityModel<Estadio> modelo = EntityModel.of(estadio,
                linkTo(methodOn(EstadioController.class).buscarPorId(estadio.getId())).withSelfRel(),
                linkTo(methodOn(EstadioController.class).atualizar(estadio.getId(), null)).withRel("update"),
                linkTo(methodOn(EstadioController.class).deletar(estadio.getId())).withRel("delete"),
                linkTo(EstadioController.class).withRel("estadios"));

        // Se o estádio já pertence a um time, aponta para ele (lado inverso do 1:1)
        if (estadio.getTime() != null) {
            modelo.add(linkTo(methodOn(TimeController.class).buscarPorId(estadio.getTime().getId())).withRel("time"));
        }
        return modelo;
    }
}
