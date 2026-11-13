// Acesso à API: versão, chave, chamadas, erros e o registro de chamadas.
// Segurança: a chave de API é digitada pelo usuário e guardada só no sessionStorage (some ao fechar a aba).

export const API = API_BASE_URL.replace(/\/+$/, ""); // API_BASE_URL vem do config.js

/* ---------- Estado guardado na aba (sessionStorage pode falhar: sempre dentro de try/catch) ---------- */

function lerSessao(chave) {
    try { return sessionStorage.getItem(chave); } catch (e) { return null; }
}

function gravarSessao(chave, valor) {
    try { sessionStorage.setItem(chave, valor); } catch (e) { /* sem armazenamento: segue sem guardar */ }
}

const estado = {
    versao: lerSessao("versaoApi") === "1" ? "1" : "2", // padrão: v2 (protegida)
    chave: lerSessao("chaveApi") || "",
    situacaoChave: "sem", // sem | verificando | valida | invalida | erro
};

export const getVersao = () => estado.versao;
export const getChave = () => estado.chave;
export const getSituacaoChave = () => estado.situacaoChave;

export function setVersao(v) {
    estado.versao = v === "1" ? "1" : "2";
    gravarSessao("versaoApi", estado.versao);
}

export function setChave(c) {
    estado.chave = c.trim();
    gravarSessao("chaveApi", estado.chave);
    estado.situacaoChave = estado.chave ? "verificando" : "sem";
}

/** Na v1 não há chave. Na v2, escrever exige uma chave que a API não tenha recusado. */
export function podeEscrever() {
    if (estado.versao === "1") return true;
    return estado.chave !== "" && estado.situacaoChave !== "invalida";
}

/* ---------- Eventos simples (quem quiser reagir a mudanças se inscreve) ---------- */

function criarEvento() {
    const ouvintes = new Set();
    return {
        ouvir(fn) { ouvintes.add(fn); return () => ouvintes.delete(fn); },
        emitir(...args) { ouvintes.forEach((fn) => fn(...args)); },
    };
}
export const eventoRegistro = criarEvento();
export const eventoLimite = criarEvento();
export const eventoChave = criarEvento();

/* ---------- Erro padronizado ---------- */

export class ErroApi extends Error {
    constructor({ status = 0, corpo = null, retryAfter = null, rede = false }) {
        super(rede ? "Falha de rede" : `HTTP ${status}`);
        this.status = status;
        this.corpo = corpo;           // ApiError da API: timestamp, status, erro, mensagem, campos
        this.retryAfter = retryAfter; // segundos (header Retry-After do 429)
        this.rede = rede;             // true: o navegador nem chegou a ler a resposta (rede ou CORS)
    }
    get mensagem() { return this.corpo && this.corpo.mensagem ? this.corpo.mensagem : ""; }
    get campos() { return this.corpo && this.corpo.campos ? this.corpo.campos : {}; }
}

/* ---------- Registro de chamadas ---------- */

export const registro = [];
const MAX_REGISTRO = 40;
let sequencia = 0;

function registrar(entrada) {
    registro.unshift(entrada);
    if (registro.length > MAX_REGISTRO) registro.pop();
    eventoRegistro.emitir(entrada);
}

/* ---------- Chamada à API ---------- */

export function novaChaveIdempotencia() {
    // crypto.randomUUID só existe em páginas seguras (https ou localhost)
    return typeof crypto.randomUUID === "function"
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

// Só segue links que apontam para a própria API (nunca manda requisição nem chave para outro endereço)
export function mesmaOrigem(url) {
    try { return new URL(url).origin === new URL(API).origin; } catch (e) { return false; }
}

/**
 * metodo: GET | POST | PUT | DELETE
 * corpo: objeto JSON (opcional)
 * idempotencyKey: só na v2, em POST
 * comChave: envia a chave também em GET (necessário só para /api-keys)
 */
export async function chamar(url, { metodo = "GET", corpo = null, idempotencyKey = null, comChave = false } = {}) {
    if (!mesmaOrigem(url)) {
        throw new Error("Endereço recusado: não pertence à API configurada.");
    }
    const v2 = estado.versao === "2";
    // A versão escolhida vai em TODAS as requisições. Na v1 não há chave de API nem idempotência.
    const headers = { "X-API-Version": estado.versao };
    if (v2 && estado.chave && (comChave || metodo !== "GET")) {
        headers["X-API-Key"] = estado.chave; // GET é público: a chave só vai nas escritas
    }
    if (corpo !== null) headers["Content-Type"] = "application/json";
    if (v2 && idempotencyKey) headers["X-Idempotency-Key"] = idempotencyKey;

    const entrada = {
        id: ++sequencia, metodo, caminho: url.slice(API.length), status: 0, ms: 0, replay: false, quando: new Date(),
        corpo, comChave, idempotencyKey: v2 && idempotencyKey ? idempotencyKey : null,
    };
    const inicio = performance.now();

    let resposta;
    try {
        resposta = await fetch(url, { method: metodo, headers, body: corpo !== null ? JSON.stringify(corpo) : undefined });
    } catch (e) {
        entrada.ms = Math.round(performance.now() - inicio);
        registrar(entrada);
        throw new ErroApi({ rede: true });
    }

    entrada.ms = Math.round(performance.now() - inicio);
    entrada.status = resposta.status;
    // Só dá para ler estes headers porque a API os "expõe" no CORS (Access-Control-Expose-Headers)
    entrada.replay = resposta.headers.get("Idempotent-Replayed") === "true";
    lerLimite(resposta);
    registrar(entrada);

    const texto = await resposta.text();
    let dados = null;
    try { dados = texto ? JSON.parse(texto) : null; } catch (e) { /* corpo não era JSON */ }

    if (!resposta.ok) {
        throw new ErroApi({ status: resposta.status, corpo: dados, retryAfter: resposta.headers.get("Retry-After") });
    }
    return { dados, resposta, replay: entrada.replay, location: resposta.headers.get("Location") };
}

/** Repete uma chamada do registro com os mesmos dados (e a mesma chave de idempotência). */
export function repetir(entrada) {
    return chamar(API + entrada.caminho, {
        metodo: entrada.metodo, corpo: entrada.corpo, idempotencyKey: entrada.idempotencyKey, comChave: entrada.comChave,
    });
}

function lerLimite(resposta) {
    const limite = resposta.headers.get("X-RateLimit-Limit");
    if (!limite) {
        eventoLimite.emitir(null);
        return;
    }
    eventoLimite.emitir({
        limite: Number(limite),
        restantes: Number(resposta.headers.get("X-RateLimit-Remaining")),
        reinicia: Number(resposta.headers.get("X-RateLimit-Reset")),
    });
}

/* ---------- Verificação da chave ---------- */

// Pergunta à API se a chave vale: /api-keys exige chave válida (200 = vale, 401 = recusada)
export async function verificarChave() {
    if (estado.versao === "1" || !estado.chave) {
        estado.situacaoChave = "sem";
        eventoChave.emitir(estado.situacaoChave);
        return;
    }
    estado.situacaoChave = "verificando";
    eventoChave.emitir(estado.situacaoChave);
    const chaveVerificada = estado.chave;
    try {
        await chamar(`${API}/api-keys?size=1`, { comChave: true });
        if (chaveVerificada === estado.chave) estado.situacaoChave = "valida";
    } catch (e) {
        if (chaveVerificada !== estado.chave) return; // o usuário já digitou outra coisa
        estado.situacaoChave = e instanceof ErroApi && e.status === 401 ? "invalida" : "erro";
    }
    eventoChave.emitir(estado.situacaoChave);
}

/* ---------- Ajudas para ler respostas HAL ---------- */

/** A lista vem em _embedded (o nome da chave, como timeList, não importa: pega a primeira lista). */
export function extrairLista(dados) {
    return dados && dados._embedded ? (Object.values(dados._embedded)[0] || []) : [];
}

/** Busca uma coleção inteira (até 200) para preencher listas de seleção. */
export async function listarTudo(caminho, ordenar = "nome,asc") {
    const { dados } = await chamar(`${API}${caminho}?size=200&sort=${ordenar}`);
    return extrairLista(dados);
}

export function linkDe(item, relacao) {
    return item && item._links && item._links[relacao] ? item._links[relacao].href : null;
}
