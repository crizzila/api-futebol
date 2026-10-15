package com.campeonato.api_futebol.repository;

import com.campeonato.api_futebol.model.Time;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TimeRepository extends JpaRepository<Time, Long> {
    // Consulta personalizada: Buscar equipas pelo nome (ignorando maiúsculas/minúsculas)
    Page<Time> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
}