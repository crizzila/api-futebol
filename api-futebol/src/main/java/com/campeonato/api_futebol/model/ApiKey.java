package com.campeonato.api_futebol.model;

import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Chave de API. A chave completa NUNCA é guardada: só o hash SHA-256 (se o banco vazar, as chaves não vazam).
 */
@Entity
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    // Hash SHA-256 da chave em hexadecimal (64 caracteres). Único para a busca ser direta.
    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    // Primeiros 8 caracteres: ajudam a identificar a chave numa listagem sem expor o segredo
    @Column(nullable = false, length = 8)
    private String prefixo;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    public ApiKey() {
    }

    /** Cria a entidade a partir da chave em texto puro (que não é armazenada). */
    public static ApiKey de(String nome, String chaveCompleta) {
        ApiKey k = new ApiKey();
        k.nome = nome;
        k.hash = sha256(chaveCompleta);
        k.prefixo = chaveCompleta.substring(0, Math.min(8, chaveCompleta.length()));
        return k;
    }

    public static String sha256(String texto) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 sempre existe no Java
        }
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getHash() { return hash; }
    public String getPrefixo() { return prefixo; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
}
