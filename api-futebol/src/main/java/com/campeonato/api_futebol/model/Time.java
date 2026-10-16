package com.campeonato.api_futebol.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade JPA "pura": nao conhece HATEOAS. Os links sao criados no TimeModelAssembler.
 */
@Entity
@Table(name = "tb_time")
@Schema(description = "Clube de futebol")
public class Time {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único do time gerado automaticamente", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank(message = "O nome do time não pode estar vazio ou conter apenas espaços.")
    @Size(min = 3, max = 50, message = "O nome do time deve ter entre 3 e 50 caracteres.")
    @Schema(description = "Nome oficial do time", example = "Palmeiras", minLength = 3, maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
    private String nome;

    @NotBlank(message = "A sigla do time é obrigatória.")
    @Pattern(regexp = "^[A-Z]{3}$", message = "A sigla deve conter exatamente 3 letras maiúsculas. Números não são permitidos.")
    @Schema(description = "Sigla do time (3 letras maiúsculas)", example = "PAL", minLength = 3, maxLength = 3, requiredMode = Schema.RequiredMode.REQUIRED)
    private String sigla;

    @NotNull(message = "A data de fundação é obrigatória.")
    @PastOrPresent(message = "A data de fundação não pode estar no futuro.")
    @Schema(description = "Data de fundação do clube", example = "1914-08-26", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dataFundacao;

    // --- OS RELACIONAMENTOS ---

    // 1:1 - Um time tem um estádio sede (Time é o dono da relação: a coluna estadio_id fica em tb_time)
    @Valid
    @NotNull(message = "O estádio do time é obrigatório.")
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "estadio_id")
    @Schema(description = "Estádio sede do time (é salvo junto com o time)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Estadio estadio;

    // 1:N - Um time tem vários jogadores (ao deletar o time, os jogadores são apagados em cascata)
    @OneToMany(mappedBy = "time", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Jogador> jogadores = new ArrayList<>();

    // N:N - Um time participa de vários campeonatos (lado inverso; o dono é Campeonato)
    @ManyToMany(mappedBy = "times")
    @JsonIgnore
    private List<Campeonato> campeonatos = new ArrayList<>();

    public Time() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getSigla() { return sigla; }
    public void setSigla(String sigla) { this.sigla = sigla; }
    public LocalDate getDataFundacao() { return dataFundacao; }
    public void setDataFundacao(LocalDate dataFundacao) { this.dataFundacao = dataFundacao; }
    public Estadio getEstadio() { return estadio; }
    public void setEstadio(Estadio estadio) { this.estadio = estadio; }
    public List<Jogador> getJogadores() { return jogadores; }
    public void setJogadores(List<Jogador> jogadores) { this.jogadores = jogadores; }
    public List<Campeonato> getCampeonatos() { return campeonatos; }
    public void setCampeonatos(List<Campeonato> campeonatos) { this.campeonatos = campeonatos; }
}
