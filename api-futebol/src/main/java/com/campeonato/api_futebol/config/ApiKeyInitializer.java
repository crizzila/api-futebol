package com.campeonato.api_futebol.config;

import com.campeonato.api_futebol.model.ApiKey;
import com.campeonato.api_futebol.repository.ApiKeyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * O H2 em memória começa vazio a cada início. Aqui criamos a chave "inicial" (vinda da variável
 * INITIAL_API_KEY) para ser possível criar as demais chaves via POST /api-keys.
 */
@Component
public class ApiKeyInitializer implements ApplicationRunner {

    private final ApiKeyRepository repository;
    private final String chaveInicial;

    public ApiKeyInitializer(ApiKeyRepository repository, @Value("${app.initial-api-key}") String chaveInicial) {
        this.repository = repository;
        this.chaveInicial = chaveInicial;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (chaveInicial == null || chaveInicial.isBlank()) {
            return; // sem chave inicial configurada, nada a criar
        }
        // Só guarda o hash; a chave em texto puro nunca vai para o banco
        if (!repository.existsByHash(ApiKey.sha256(chaveInicial))) {
            repository.save(ApiKey.de("inicial", chaveInicial));
        }
    }
}
