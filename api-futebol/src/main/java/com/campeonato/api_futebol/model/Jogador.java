package com.campeonato.api_futebol.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "tb_jogador")
public class Jogador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único do jogador", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank(message = "O nome do jogador é obrigatório.")
    @Size(min = 2, max = 80, message = "O nome deve ter entre 2 e 80 caracteres.")
    @Schema(description = "Nome completo ou apelido do jogador", example = "Raphael Veiga", minLength = 2, maxLength = 80, requiredMode = Schema.RequiredMode.REQUIRED)
    private String nome;

    @NotNull(message = "A posição do jogador é obrigatória.")
    @Enumerated(EnumType.STRING)
    @Schema(description = "Posição tática do jogador no campo", example = "MEIA", requiredMode = Schema.RequiredMode.REQUIRED)
    private PosicaoJogador posicao;

    // O RELACIONAMENTO N:1
    // Vários jogadores pertencem a um time
    @ManyToOne
    @JoinColumn(name = "time_id")
    private Time time;

    public Jogador() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public PosicaoJogador getPosicao() { return posicao; }
    public void setPosicao(PosicaoJogador posicao) { this.posicao = posicao; }
    public Time getTime() { return time; }
    public void setTime(Time time) { this.time = time; }
}