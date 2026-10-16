package com.campeonato.api_futebol.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_partida")
@Schema(description = "Partida: entidade associativa entre dois times (mandante e visitante) dentro de um campeonato")
public class Partida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único da partida", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotNull(message = "A data e hora da partida são obrigatórias.")
    @Schema(description = "Data e hora em que o jogo irá acontecer", example = "2026-10-15T21:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Schema(description = "Situação atual do jogo", example = "AGENDADA")
    private StatusPartida status = StatusPartida.AGENDADA;

    @Min(value = 0, message = "O placar não pode ser negativo.")
    @Schema(description = "Gols marcados pelo time da casa", example = "2")
    private Integer placarMandante = 0;

    @Min(value = 0, message = "O placar não pode ser negativo.")
    @Schema(description = "Gols marcados pelo time visitante", example = "1")
    private Integer placarVisitante = 0;

    // OS RELACIONAMENTOS N:1
    @NotNull(message = "O time mandante é obrigatório.")
    @ManyToOne
    @JoinColumn(name = "mandante_id")
    @Schema(description = "Time que joga em casa. Basta informar o id: { \"id\": 1 }", requiredMode = Schema.RequiredMode.REQUIRED)
    private Time mandante;

    @NotNull(message = "O time visitante é obrigatório.")
    @ManyToOne
    @JoinColumn(name = "visitante_id")
    @Schema(description = "Time visitante. Basta informar o id: { \"id\": 2 }", requiredMode = Schema.RequiredMode.REQUIRED)
    private Time visitante;

    @NotNull(message = "O campeonato é obrigatório.")
    @ManyToOne
    @JoinColumn(name = "campeonato_id")
    @Schema(description = "Campeonato da partida. Basta informar o id: { \"id\": 1 }", requiredMode = Schema.RequiredMode.REQUIRED)
    private Campeonato campeonato;

    public Partida() {
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
    public StatusPartida getStatus() { return status; }
    public void setStatus(StatusPartida status) { this.status = status; }
    public Integer getPlacarMandante() { return placarMandante; }
    public void setPlacarMandante(Integer placarMandante) { this.placarMandante = placarMandante; }
    public Integer getPlacarVisitante() { return placarVisitante; }
    public void setPlacarVisitante(Integer placarVisitante) { this.placarVisitante = placarVisitante; }
    public Time getMandante() { return mandante; }
    public void setMandante(Time mandante) { this.mandante = mandante; }
    public Time getVisitante() { return visitante; }
    public void setVisitante(Time visitante) { this.visitante = visitante; }
    public Campeonato getCampeonato() { return campeonato; }
    public void setCampeonato(Campeonato campeonato) { this.campeonato = campeonato; }
}