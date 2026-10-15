package com.campeonato.api_futebol.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "tb_time")
//herdando as config do HATEOAS
public class Time extends org.springframework.hateoas.RepresentationModel<Time>{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único do time gerado automaticamente", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank(message = "O nome do time não pode estar vazio ou conter apenas espaços.")
    @Size(min = 3, max = 50, message = "O nome do time deve ter entre 3 e 50 caracteres.")
    @Schema(description = "Nome oficial do time", example = "Palmeiras", minLength = 3, maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
    private String nome;

    @NotBlank(message = "A sigla do time é obrigatória.")
    @Size(min = 3, max = 3, message = "A sigla deve ter exatamente 3 caracteres.")
    @Pattern(regexp = "^[A-Z]{3}$", message = "A sigla deve conter exatamente 3 letras maiúsculas. Números não são permitidos.")
    @Schema(description = "Sigla do time", example = "PAL", minLength = 3, maxLength = 3, requiredMode = Schema.RequiredMode.REQUIRED)
    private String sigla;

    @NotNull(message = "A data de fundação é obrigatória.")
    @Schema(description = "Data de fundação do clube", example = "1914-08-26", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dataFundacao;

    // --- OS RELACIONAMENTOS ---

    // 1:1 - Um time tem um estádio sede
    @OneToOne (cascade = CascadeType.ALL) // <-- ADICIONEI O CASCADE AQUI
    @JoinColumn(name = "estadio_id")
    private Estadio estadio;

    // 1:N - Um time tem vários jogadores
    @OneToMany(mappedBy = "time", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Jogador> jogadores;

    // N:N - Um time joga vários campeonatos
    @ManyToMany(mappedBy = "times")
    @JsonIgnore
    private List<Campeonato> campeonatos;

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