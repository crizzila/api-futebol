package com.campeonato.api_futebol.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captura os erros de toda a API em um único lugar e devolve sempre o mesmo formato (ApiError).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bean Validation falhou (@NotBlank, @Size, @Pattern...) -> 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> tratarValidacao(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        return montar(HttpStatus.BAD_REQUEST, "Dados inválidos. Verifique os campos informados.", campos);
    }

    // Ordenação por campo que não existe (ex.: sort=string) -> 400 (antes dava 500)
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> tratarOrdenacaoInvalida(PropertyReferenceException ex) {
        return montar(HttpStatus.BAD_REQUEST,
                "Ordenação inválida: o campo '" + ex.getPropertyName() + "' não existe. Exemplo válido: sort=id,asc", null);
    }

    // JSON malformado, data invalida, valor de enum inexistente -> 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> tratarJsonInvalido(HttpMessageNotReadableException ex) {
        return montar(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido. Verifique o JSON, o formato das datas e os valores de enums.", null);
    }

    // Parâmetro com tipo errado (ex.: status=XYZ) -> 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tratarTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return montar(HttpStatus.BAD_REQUEST,
                "Valor inválido para o parâmetro '" + ex.getName() + "'.", null);
    }

    // Parâmetro obrigatório ausente -> 400
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> tratarParâmetroAusente(MissingServletRequestParameterException ex) {
        return montar(HttpStatus.BAD_REQUEST,
                "O parâmetro '" + ex.getParameterName() + "' é obrigatório.", null);
    }

    // Erros lancados pelos controllers (404, 400, 409...)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> tratarStatus(ResponseStatusException ex) {
        return montar(ex.getStatusCode(), ex.getReason(), null);
    }

    // Violação de integridade no banco -> 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> tratarIntegridade(DataIntegrityViolationException ex) {
        return montar(HttpStatus.CONFLICT,
                "Operação não permitida: o registro está relacionado a outros dados.", null);
    }

    private ResponseEntity<ApiError> montar(HttpStatusCode status, String mensagem, Map<String, String> campos) {
        HttpStatus http = HttpStatus.resolve(status.value());
        String nome = http != null ? http.getReasonPhrase() : String.valueOf(status.value());
        ApiError corpo = new ApiError(LocalDateTime.now(), status.value(), nome, mensagem, campos);
        return ResponseEntity.status(status).body(corpo);
    }
}
