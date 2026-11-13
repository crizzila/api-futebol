package com.campeonato.api_futebol.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Aplica o CORS só quando a requisição é da v2 (ou /api-keys). Na v1 não há cabeçalhos CORS.
 * Detalhe do pré-voo (OPTIONS): o navegador NÃO envia o X-API-Version no OPTIONS, só avisa em
 * Access-Control-Request-Headers que vai enviá-lo. Por isso esse aviso identifica a v2 no pré-voo.
 */
public class CorsPorVersaoFilter extends OncePerRequestFilter {

    private final CorsFilter cors;

    public CorsPorVersaoFilter(CorsFilter cors) {
        this.cors = cors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (deveAplicarCors(request)) {
            cors.doFilter(request, response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean deveAplicarCors(HttpServletRequest request) {
        boolean preVoo = "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Access-Control-Request-Method") != null;
        if (preVoo) {
            String pedidos = request.getHeader("Access-Control-Request-Headers");
            return ApiVersion.ehApiKeys(request)
                    || (pedidos != null && pedidos.toLowerCase().contains("x-api-version"));
        }
        return ApiVersion.usaProtecoesV2(request);
    }
}
