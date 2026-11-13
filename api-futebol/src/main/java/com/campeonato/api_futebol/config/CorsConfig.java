package com.campeonato.api_futebol.config;

import com.campeonato.api_futebol.filter.CorsPorVersaoFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS: define quais sites (origens) o navegador pode deixar chamar esta API.
 * Usamos um CorsFilter com a MAIOR precedência (roda antes de todos os outros filtros).
 * Por quê: se o CORS rodasse depois do ApiKeyFilter/RateLimitFilter, as respostas 401/429 sairiam sem os
 * cabeçalhos CORS e o navegador mostraria "erro de CORS" em vez do erro real.
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsPorVersaoFilter> corsFilter(
            // Lista separada por vírgula (variável CORS_ALLOWED_ORIGINS). NUNCA usar "*".
            @Value("${app.cors.allowed-origins}") String origensPermitidas) {

        List<String> origens = Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-API-Key", "X-Idempotency-Key", "X-API-Version"));
        // Cabeçalhos que o JavaScript do navegador só consegue LER se forem "expostos" aqui
        config.setExposedHeaders(List.of("Location", "Retry-After", "X-RateLimit-Limit",
                "X-RateLimit-Remaining", "X-RateLimit-Reset", "Idempotent-Replayed"));
        config.setMaxAge(3600L); // o navegador guarda a resposta do OPTIONS por 1 hora

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", config);

        // O CorsPorVersaoFilter só chama o CorsFilter nas requisições da v2 (a v1 não tem CORS)
        FilterRegistrationBean<CorsPorVersaoFilter> registro =
                new FilterRegistrationBean<>(new CorsPorVersaoFilter(new CorsFilter(fonte)));
        // HIGHEST_PRECEDENCE + 1: logo depois do filtro do Spring que lê X-Forwarded-Proto/Host (que usa a
        // precedência máxima). Assim o CORS enxerga o endereço público (https) atrás do proxy do Render;
        // sem isso, o POST do próprio Swagger seria tratado como "outra origem" e barrado com 403.
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registro;
    }
}
