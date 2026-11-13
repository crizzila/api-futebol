package com.campeonato.api_futebol.assembler;

import com.campeonato.api_futebol.controller.ApiKeyController;
import com.campeonato.api_futebol.controller.ApiKeyController.ApiKeyResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ApiKeyModelAssembler implements RepresentationModelAssembler<ApiKeyResponse, EntityModel<ApiKeyResponse>> {

    @Override
    public EntityModel<ApiKeyResponse> toModel(ApiKeyResponse chave) {
        return EntityModel.of(chave,
                linkTo(methodOn(ApiKeyController.class).revogar(chave.id())).withRel("revoke"),
                linkTo(ApiKeyController.class).withRel("api-keys"));
    }
}
