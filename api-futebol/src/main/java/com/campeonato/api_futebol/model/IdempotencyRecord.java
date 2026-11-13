package com.campeonato.api_futebol.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Guarda o resultado de um POST feito com X-Idempotency-Key, para poder devolver a mesma resposta
 * se o cliente repetir a requisição (em vez de criar o recurso de novo).
 * Enquanto o POST está sendo processado, "status" fica nulo (= em processamento).
 */
@Entity
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Valor do header X-Idempotency-Key. UNIQUE: o banco impede duas requisições simultâneas com a mesma chave
    @Column(nullable = false, unique = true)
    private String chave;

    // Ex.: "POST /estadios". Usado para detectar a mesma chave sendo reaproveitada em outra rota
    @Column(nullable = false)
    private String metodoCaminho;

    // SHA-256 do corpo da requisição. Usado para detectar a mesma chave com um corpo diferente
    @Column(nullable = false, length = 64)
    private String hashCorpo;

    // Status HTTP da resposta guardada; nulo enquanto a primeira requisição ainda está em processamento
    private Integer status;

    @Column(length = 100000)
    private String corpoResposta;

    private String location;

    private String contentType;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String chave, String metodoCaminho, String hashCorpo) {
        this.chave = chave;
        this.metodoCaminho = metodoCaminho;
        this.hashCorpo = hashCorpo;
    }

    /** Marca como concluído, guardando a resposta que será reenviada nas repetições. */
    public void concluir(int status, String corpoResposta, String location, String contentType) {
        this.status = status;
        this.corpoResposta = corpoResposta;
        this.location = location;
        this.contentType = contentType;
    }

    public boolean emProcessamento() {
        return status == null;
    }

    public Long getId() { return id; }
    public String getChave() { return chave; }
    public String getMetodoCaminho() { return metodoCaminho; }
    public String getHashCorpo() { return hashCorpo; }
    public Integer getStatus() { return status; }
    public String getCorpoResposta() { return corpoResposta; }
    public String getLocation() { return location; }
    public String getContentType() { return contentType; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
