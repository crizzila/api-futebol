package com.campeonato.api_futebol.filter;

import com.campeonato.api_futebol.exception.ApiError;
import com.campeonato.api_futebol.model.ApiKey;
import com.campeonato.api_futebol.repository.ApiKeyRepository;
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
 * Exige a chave no header X-API-Key nas operações que alteram dados.
 * Regras: todo GET é público (exceto /api-keys); POST/PUT/DELETE nos recursos e TUDO em /api-keys exigem chave.
 * Filtros rodam antes do Spring MVC, então o @RestControllerAdvice não pega nossos erros: escrevemos o JSON aqui.
 */
@Component
@Order(20) // as próximas etapas usam ordens menores (CORS, rate limit) e maiores (idempotência)
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    // Recursos cujas escritas (POST/PUT/DELETE) são protegidas
    private static final Set<String> RECURSOS = Set.of("/times", "/estadios", "/jogadores", "/campeonatos", "/partidas");

    private final ApiKeyRepository repository;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(ApiKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // Só entra no filtro o que precisa de chave; Swagger, H2, estáticos, GETs e OPTIONS passam direto
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String metodo = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(metodo)) {
            return true; // pré-requisição do CORS não leva cabeçalhos customizados
        }
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if (caminho.equals("/api-keys") || caminho.startsWith("/api-keys/")) {
            return false; // /api-keys: qualquer método (inclusive GET) exige chave
        }
        boolean escrita = "POST".equalsIgnoreCase(metodo) || "PUT".equalsIgnoreCase(metodo)
                || "DELETE".equalsIgnoreCase(metodo) || "PATCH".equalsIgnoreCase(metodo);
        return !(escrita && RECURSOS.stream().anyMatch(r -> caminho.equals(r) || caminho.startsWith(r + "/")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String chave = request.getHeader(HEADER);
        // Compara o HASH da chave recebida com os hashes ativos no banco
        if (chave == null || chave.isBlank() || !repository.existsByHashAndAtivaTrue(ApiKey.sha256(chave.trim()))) {
            String msg = (chave == null || chave.isBlank())
                    ? "Chave de API ausente. Envie o header X-API-Key."
                    : "Chave de API inválida ou revogada.";
            escreverErro(response, msg);
            return;
        }
        chain.doFilter(request, response);
    }

    private void escreverErro(HttpServletResponse response, String mensagem) throws IOException {
        ApiError erro = new ApiError(LocalDateTime.now(), 401, "Unauthorized", mensagem, null);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(erro));
    }
}
