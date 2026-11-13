package com.campeonato.api_futebol.filter;

import com.campeonato.api_futebol.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita quantas requisições cada cliente (IP) pode fazer por minuto, em memória, sem biblioteca externa.
 * Técnica: "janela fixa" de 1 minuto. Cada IP tem um contador que zera quando a janela de 60s termina.
 * Roda ANTES do ApiKeyFilter (ordem 10 < 20): assim até as requisições sem chave (401) contam no limite.
 * Como é filtro, o erro 429 é escrito aqui à mão (o @RestControllerAdvice não alcança filtros).
 */
@Component
@Order(10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long JANELA_MS = 60_000;

    // Só os endpoints da API são limitados; Swagger, H2 e arquivos estáticos ficam de fora
    private static final Set<String> RECURSOS = Set.of(
            "/times", "/estadios", "/jogadores", "/campeonatos", "/partidas", "/api-keys");

    // Estado de um cliente: quando a janela começou e quantas requisições já fez nela
    private static class Janela {
        final long inicio;
        int contador;

        Janela(long inicio) {
            this.inicio = inicio;
        }
    }

    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    private final int limite;
    private final ObjectMapper objectMapper;
    private volatile long ultimaLimpeza = System.currentTimeMillis();

    public RateLimitFilter(@Value("${app.rate-limit.requests-per-minute}") int limite, ObjectMapper objectMapper) {
        this.limite = limite;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true; // pré-requisição do CORS não conta
        }
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return RECURSOS.stream().noneMatch(r -> caminho.equals(r) || caminho.startsWith(r + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long agora = System.currentTimeMillis();
        limparJanelasVencidas(agora);

        // compute() é atômico por chave: duas requisições simultâneas do mesmo IP não se atropelam
        Janela janela = janelas.compute(identificarCliente(request), (ip, atual) -> {
            Janela j = (atual == null || agora - atual.inicio >= JANELA_MS) ? new Janela(agora) : atual;
            j.contador++;
            return j;
        });

        int restantes = Math.max(0, limite - janela.contador);
        long segundosAteReset = Math.max(1, (janela.inicio + JANELA_MS - agora + 999) / 1000); // arredonda p/ cima

        response.setHeader("X-RateLimit-Limit", String.valueOf(limite));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(restantes));
        response.setHeader("X-RateLimit-Reset", String.valueOf(segundosAteReset));

        if (janela.contador > limite) {
            response.setHeader("Retry-After", String.valueOf(segundosAteReset));
            escreverErro(response, "Limite de " + limite + " requisições por minuto excedido. Tente novamente em "
                    + segundosAteReset + " segundos.");
            return;
        }
        chain.doFilter(request, response);
    }

    // Atrás do proxy do Render, o IP real vem no X-Forwarded-For (primeiro valor); senão usa o IP da conexão
    private String identificarCliente(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // No máximo 1x por minuto remove os IPs inativos, para o mapa não crescer para sempre
    private void limparJanelasVencidas(long agora) {
        if (agora - ultimaLimpeza < JANELA_MS) {
            return;
        }
        ultimaLimpeza = agora;
        janelas.entrySet().removeIf(e -> agora - e.getValue().inicio >= JANELA_MS);
    }

    private void escreverErro(HttpServletResponse response, String mensagem) throws IOException {
        ApiError erro = new ApiError(LocalDateTime.now(), 429, "Too Many Requests", mensagem, null);
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(erro));
    }
}
