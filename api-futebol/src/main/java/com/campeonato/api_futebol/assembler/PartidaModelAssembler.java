package com.campeonato.api_futebol.assembler;

import com.campeonato.api_futebol.controller.CampeonatoController;
import com.campeonato.api_futebol.controller.PartidaController;
import com.campeonato.api_futebol.controller.TimeController;
import com.campeonato.api_futebol.model.Partida;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PartidaModelAssembler implements RepresentationModelAssembler<Partida, EntityModel<Partida>> {

    @Override
    public EntityModel<Partida> toModel(Partida partida) {
        EntityModel<Partida> modelo = EntityModel.of(partida,
                linkTo(methodOn(PartidaController.class).buscarPorId(partida.getId())).withSelfRel(),
                linkTo(methodOn(PartidaController.class).atualizar(partida.getId(), null)).withRel("update"),
                linkTo(methodOn(PartidaController.class).deletar(partida.getId())).withRel("delete"),
                linkTo(PartidaController.class).withRel("partidas"));

        if (partida.getMandante() != null) {
            modelo.add(linkTo(methodOn(TimeController.class).buscarPorId(partida.getMandante().getId())).withRel("mandante"));
        }
        if (partida.getVisitante() != null) {
            modelo.add(linkTo(methodOn(TimeController.class).buscarPorId(partida.getVisitante().getId())).withRel("visitante"));
        }
        if (partida.getCampeonato() != null) {
            modelo.add(linkTo(methodOn(CampeonatoController.class).buscarPorId(partida.getCampeonato().getId())).withRel("campeonato"));
        }
        return modelo;
    }
}
