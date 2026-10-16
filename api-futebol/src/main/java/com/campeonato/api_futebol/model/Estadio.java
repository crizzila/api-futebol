package com.campeonato.api_futebol.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "tb_estadio")
@Schema(description = "Estádio sede de um time")
public class Estadio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único do estádio", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank(message = "O nome do estádio é obrigatório e não pode ficar em branco.")
    @Size(min = 3, max = 100, message = "O nome do estádio deve ter entre 3 e 100 caracteres.")
    @Schema(description = "Nome oficial do estádio", example = "Maracanã", minLength = 3, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
    private String nome;

    @NotNull(message = "A capacidade é obrigatória.")
    @Min(value = 1000, message = "A capacidade mínima para um estádio no campeonato é de 1000 lugares.")
    @Schema(description = "Capacidade total de público", example = "78000", minimum = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer capacidade;

    // 1:1 - lado inverso (mappedBy): quem guarda a chave estrangeira é o Time
    @OneToOne(mappedBy = "estadio")
    @JsonIgnore
    private Time time;

    public Estadio() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Integer getCapacidade() { return capacidade; }
    public void setCapacidade(Integer capacidade) { this.capacidade = capacidade; }
    public Time getTime() { return time; }
    public void setTime(Time time) { this.time = time; }
}