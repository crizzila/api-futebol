package com.campeonato.api_futebol.assembler;

import com.campeonato.api_futebol.controller.CampeonatoController;
import com.campeonato.api_futebol.model.Campeonato;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class CampeonatoModelAssembler implements RepresentationModelAssembler<Campeonato, EntityModel<Campeonato>> {

    @Override
    public EntityModel<Campeonato> toModel(Campeonato campeonato) {
        return EntityModel.of(campeonato,
                linkTo(methodOn(CampeonatoController.class).buscarPorId(campeonato.getId())).withSelfRel(),
                linkTo(methodOn(CampeonatoController.class).atualizar(campeonato.getId(), null)).withRel("update"),
                linkTo(methodOn(CampeonatoController.class).deletar(campeonato.getId())).withRel("delete"),
                linkTo(CampeonatoController.class).withRel("campeonatos"));
    }
}
