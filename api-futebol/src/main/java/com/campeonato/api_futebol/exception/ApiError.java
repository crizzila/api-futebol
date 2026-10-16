package com.campeonato.api_futebol.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato padrão de resposta de erro da API.
 */
@Schema(description = "Resposta padrão de erro da API")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        @Schema(description = "Momento em que o erro ocorreu", example = "2026-10-15T20:30:00")
        LocalDateTime timestamp,

        @Schema(description = "Código HTTP do erro", example = "400")
        int status,

        @Schema(description = "Nome do status HTTP", example = "Bad Request")
        String erro,

        @Schema(description = "Mensagem explicando o que deu errado", example = "Dados inválidos. Verifique os campos informados.")
        String mensagem,

        @Schema(description = "Erros por campo (somente em erros de validação)",
                example = "{\"sigla\": \"A sigla deve conter exatamente 3 letras maiúsculas.\"}")
        Map<String, String> campos) {
}
