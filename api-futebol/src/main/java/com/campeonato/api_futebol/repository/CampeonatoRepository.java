package com.campeonato.api_futebol.repository;

import com.campeonato.api_futebol.model.Campeonato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampeonatoRepository extends JpaRepository<Campeonato, Long> {
    // Consulta personalizada: Buscar campeonatos por ano
    Page<Campeonato> findByAno(Integer ano, Pageable pageable);

    // Consulta personalizada: buscar campeonatos pelo nome
    Page<Campeonato> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
}