package com.campeonato.api_futebol.filter;

import com.campeonato.api_futebol.exception.ApiError;
import com.campeonato.api_futebol.model.IdempotencyRecord;
import com.campeonato.api_futebol.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * Idempotência: repetir o mesmo POST (mesma chave em X-Idempotency-Key) não cria o recurso duas vezes.
 * Roda DEPOIS do ApiKeyFilter (ordem 30 > 20): só chaves de API válidas chegam aqui.
 *
 * Fluxo:
 *  1. Chave nova: grava um registro "em processamento", executa o POST e, se a resposta for 2xx, guarda a resposta.
 *  2. Mesma chave + mesmo método/rota + mesmo corpo: devolve a resposta guardada (Idempotent-Replayed: true).
 *  3. Mesma chave com corpo ou rota diferente: 422.   4. Mesma chave ainda em processamento: 409.
 */
@Component
@Order(30)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Idempotency-Key";

    // Recursos onde a idempotência vale (somente POST). /api-keys fica de fora de propósito.
    private static final Set<String> RECURSOS = Set.of("/times", "/estadios", "/jogadores", "/campeonatos", "/partidas");

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // Só POST nos recursos acima e somente se o cliente mandou o header; senão processa normalmente
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String chave = request.getHeader(HEADER);
        if (chave == null || chave.isBlank()) {
            return true;
        }
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return RECURSOS.stream().noneMatch(r -> caminho.equals(r) || caminho.startsWith(r + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String chave = request.getHeader(HEADER).trim();
        if (chave.length() > 255) {
            escreverErro(response, 400, "Bad Request", "O header " + HEADER + " deve ter no máximo 255 caracteres.");
            return;
        }

        // O corpo de uma requisição só pode ser lido uma vez. Lemos aqui para calcular o hash e
        // entregamos ao controller uma cópia (wrapper) que pode ser lida de novo.
        byte[] corpo = request.getInputStream().readAllBytes();
        String assinatura = request.getMethod() + " " + request.getRequestURI();
        String hash = sha256(corpo);

        Optional<IdempotencyRecord> existente = repository.findByChave(chave);
        if (existente.isPresent()) {
            responderParaChaveExistente(existente.get(), assinatura, hash, response);
            return;
        }

        // Chave nova: reserva a chave ANTES de processar. O UNIQUE do banco garante que, se duas
        // requisições iguais chegarem juntas, só uma consegue reservar.
        IdempotencyRecord registro = new IdempotencyRecord(chave, assinatura, hash);
        try {
            repository.saveAndFlush(registro);
        } catch (DataIntegrityViolationException e) {
            escreverErro(response, 409, "Conflict", mensagemEmProcessamento());
            return;
        }

        ContentCachingResponseWrapper respostaCacheada = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(new CorpoRepetivelRequest(request, corpo), respostaCacheada);

            int status = respostaCacheada.getStatus();
            if (status >= 200 && status < 300) {
                // Só respostas de sucesso são guardadas
                registro.concluir(status,
                        new String(respostaCacheada.getContentAsByteArray(), StandardCharsets.UTF_8),
                        respostaCacheada.getHeader("Location"),
                        respostaCacheada.getContentType());
                repository.save(registro);
            } else {
                repository.delete(registro); // erro: libera a chave para o cliente tentar de novo
            }
        } catch (IOException | ServletException | RuntimeException e) {
            repository.delete(registro);
            throw e;
        } finally {
            respostaCacheada.copyBodyToResponse(); // o wrapper segura o corpo; aqui ele é enviado ao cliente
        }
    }

    private void responderParaChaveExistente(IdempotencyRecord registro, String assinatura, String hash,
                                             HttpServletResponse response) throws IOException {
        if (!registro.getMetodoCaminho().equals(assinatura) || !registro.getHashCorpo().equals(hash)) {
            escreverErro(response, 422, "Unprocessable Entity",
                    "Esta chave de idempotência já foi usada com outra requisição (corpo ou rota diferente). "
                            + "Use uma chave nova para uma nova operação.");
            return;
        }
        if (registro.emProcessamento()) {
            escreverErro(response, 409, "Conflict", mensagemEmProcessamento());
            return;
        }
        // Repetição legítima: devolve exatamente a resposta guardada, sem executar o POST de novo
        response.setStatus(registro.getStatus());
        if (registro.getContentType() != null) {
            response.setContentType(registro.getContentType());
        }
        if (registro.getLocation() != null) {
            response.setHeader("Location", registro.getLocation());
        }
        response.setHeader("Idempotent-Replayed", "true");
        byte[] bytes = registro.getCorpoResposta() == null ? new byte[0]
                : registro.getCorpoResposta().getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private String mensagemEmProcessamento() {
        return "Já existe uma requisição em processamento com esta chave de idempotência. Tente novamente em instantes.";
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 sempre existe no Java
        }
    }

    // Filtro não passa pelo @RestControllerAdvice: escrevemos o JSON do ApiError à mão
    private void escreverErro(HttpServletResponse response, int status, String erro, String mensagem) throws IOException {
        ApiError corpo = new ApiError(LocalDateTime.now(), status, erro, mensagem, null);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(corpo));
    }

    /** Request que devolve o corpo já lido (guardado em memória), podendo ser lido quantas vezes precisar. */
    private static class CorpoRepetivelRequest extends HttpServletRequestWrapper {
        private final byte[] corpo;

        CorpoRepetivelRequest(HttpServletRequest request, byte[] corpo) {
            super(request);
            this.corpo = corpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream origem = new ByteArrayInputStream(corpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return origem.read();
                }

                @Override
                public boolean isFinished() {
                    return origem.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String codificacao = getCharacterEncoding();
            Charset charset = codificacao == null ? StandardCharsets.UTF_8 : Charset.forName(codificacao);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
