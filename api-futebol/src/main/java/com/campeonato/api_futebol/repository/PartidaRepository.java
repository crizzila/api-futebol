package com.campeonato.api_futebol.repository;

import com.campeonato.api_futebol.model.Partida;
import com.campeonato.api_futebol.model.StatusPartida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PartidaRepository extends JpaRepository<Partida, Long> {
    // Consulta personalizada: Buscar partidas pelo status (Agendada, Finalizada, etc.)
    Page<Partida> findByStatus(StatusPartida status, Pageable pageable);
}