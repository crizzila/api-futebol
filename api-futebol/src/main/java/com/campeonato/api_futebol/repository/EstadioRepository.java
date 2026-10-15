package com.campeonato.api_futebol.repository;

import com.campeonato.api_futebol.model.Estadio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstadioRepository extends JpaRepository<Estadio, Long> {
    // Consulta personalizada: Buscar estádios com capacidade maior ou igual a X
    Page<Estadio> findByCapacidadeGreaterThanEqual(Integer capacidade, Pageable pageable);
}