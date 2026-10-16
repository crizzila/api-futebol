package com.campeonato.api_futebol.assembler;

import com.campeonato.api_futebol.controller.JogadorController;
import com.campeonato.api_futebol.controller.TimeController;
import com.campeonato.api_futebol.model.Jogador;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class JogadorModelAssembler implements RepresentationModelAssembler<Jogador, EntityModel<Jogador>> {

    @Override
    public EntityModel<Jogador> toModel(Jogador jogador) {
        EntityModel<Jogador> modelo = EntityModel.of(jogador,
                linkTo(methodOn(JogadorController.class).buscarPorId(jogador.getId())).withSelfRel(),
                linkTo(methodOn(JogadorController.class).atualizar(jogador.getId(), null)).withRel("update"),
                linkTo(methodOn(JogadorController.class).deletar(jogador.getId())).withRel("delete"),
                linkTo(JogadorController.class).withRel("jogadores"));

        if (jogador.getTime() != null) {
            modelo.add(linkTo(methodOn(TimeController.class).buscarPorId(jogador.getTime().getId())).withRel("time"));
        }
        return modelo;
    }
}
