package com.campeonato.api_futebol.repository;

import com.campeonato.api_futebol.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    // O filtro usa estes métodos: busca pelo hash da chave recebida
    boolean existsByHashAndAtivaTrue(String hash);

    boolean existsByHash(String hash);
}
