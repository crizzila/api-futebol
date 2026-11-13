package com.campeonato.api_futebol.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Regras de versão da API, num lugar só (usadas por todos os filtros).
 *  - v1 (sem header ou "1"): comportamento original, SEM chave de API, rate limit, CORS e idempotência.
 *  - v2 ("2"): todas as proteções ativas.
 * Exceção: /api-keys é um recurso novo (não existia na v1) e sempre usa as proteções da v2.
 */
public final class ApiVersion {

    public static final String HEADER = "X-API-Version";

    private ApiVersion() {
    }

    /** Versão pedida no header: 1 (ausente ou "1"), 2 ("2") ou null se o valor for inválido. */
    public static Integer pedida(HttpServletRequest request) {
        String valor = request.getHeader(HEADER);
        if (valor == null || valor.isBlank()) {
            return 1;
        }
        return switch (valor.trim()) {
            case "1" -> 1;
            case "2" -> 2;
            default -> null;
        };
    }

    public static boolean ehApiKeys(HttpServletRequest request) {
        String caminho = caminho(request);
        return caminho.equals("/api-keys") || caminho.startsWith("/api-keys/");
    }

    /** true quando as proteções (chave, rate limit, idempotência, CORS) devem valer nesta requisição. */
    public static boolean usaProtecoesV2(HttpServletRequest request) {
        if (ehApiKeys(request)) {
            return true;
        }
        // 2 ou inválido: valor inválido segue o caminho protegido e depois recebe 400 do ApiVersionFilter
        return !Integer.valueOf(1).equals(pedida(request));
    }

    private static String caminho(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
