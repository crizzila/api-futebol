package com.campeonato.api_futebol.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_campeonato")
@Schema(description = "Campeonato de futebol disputado por vários times")
public class Campeonato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único do campeonato", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank(message = "O nome do campeonato não pode estar vazio.")
    @Size(min = 5, max = 100, message = "O nome do campeonato deve ter entre 5 e 100 caracteres.")
    @Schema(description = "Nome oficial da competição", example = "Paulistão", minLength = 5, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
    private String nome;

    @NotNull(message = "O ano do campeonato é obrigatório.")
    @Min(value = 1888, message = "O ano não pode ser inferior a 1888.")
    @Schema(description = "Ano da temporada do campeonato", example = "2026", minimum = "1888", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer ano;

    // O RELACIONAMENTO N:N
    // Vários campeonatos têm vários times
    @ManyToMany
    @JoinTable(
            name = "tb_campeonato_time",
            joinColumns = @JoinColumn(name = "campeonato_id"),
            inverseJoinColumns = @JoinColumn(name = "time_id")
    )
    @Schema(description = "Times participantes. Na criação informe só os ids: [{ \"id\": 1 }, { \"id\": 2 }]")
    private List<Time> times = new ArrayList<>();

    public Campeonato() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }
    public List<Time> getTimes() { return times; }
    public void setTimes(List<Time> times) { this.times = times; }
}