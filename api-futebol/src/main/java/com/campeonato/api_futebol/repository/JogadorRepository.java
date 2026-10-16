package com.campeonato.api_futebol.repository;

import com.campeonato.api_futebol.model.Jogador;
import com.campeonato.api_futebol.model.PosicaoJogador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JogadorRepository extends JpaRepository<Jogador, Long> {
    // Consulta personalizada: Buscar jogadores por uma posição específica
    Page<Jogador> findByPosicao(PosicaoJogador posicao, Pageable pageable);

    // Consulta personalizada: buscar jogadores pelo nome (contém, sem diferenciar maiúsculas)
    Page<Jogador> findByNomeContainingIgnoreCase(String nome, Pageable pageable);

    // Jogadores de um time (navega pelo relacionamento: time.id)
    Page<Jogador> findByTimeId(Long timeId, Pageable pageable);
}
