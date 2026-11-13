package com.campeonato.api_futebol.filter;

import com.campeonato.api_futebol.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Valida o header X-API-Version (só 1 ou 2; qualquer outro valor = 400) e devolve na resposta a versão usada.
 * Roda antes dos demais filtros (ordem 5), depois do CORS.
 */
@Component
@Order(5)
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final Set<String> RECURSOS = Set.of("/times", "/estadios", "/jogadores", "/campeonatos", "/partidas");

    private final ObjectMapper objectMapper;

    public ApiVersionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Só os 5 recursos usam versão (/api-keys sempre é v2; Swagger e estáticos não têm versão)
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return RECURSOS.stream().noneMatch(r -> caminho.equals(r) || caminho.startsWith(r + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Integer versao = ApiVersion.pedida(request);
        if (versao == null) {
            ApiError erro = new ApiError(LocalDateTime.now(), 400, "Bad Request",
                    "Valor inválido para o header X-API-Version. Use 1 ou 2.", null);
            response.setStatus(400);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(erro));
            return;
        }
        response.setHeader(ApiVersion.HEADER, String.valueOf(versao));
        chain.doFilter(request, response);
    }
}
