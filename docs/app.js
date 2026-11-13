"use strict";

// Cliente web da API. JavaScript puro, sem build. Usa as variáveis de config.js.
// Segurança: a chave de API é digitada pelo usuário e guardada só no sessionStorage;
// os dados vindos da API são mostrados com textContent (nunca innerHTML), para não executar HTML injetado.

const API = API_BASE_URL.replace(/\/+$/, "");
const TAMANHO_PAGINA = 5;

const $ = (id) => document.getElementById(id);

// Links da página atual (HATEOAS): o botão Anterior/Próxima só segue o que a API informa em _links
let linksPagina = {};
// Último cadastro enviado, para demonstrar a idempotência reenviando com a MESMA chave
let ultimoCadastro = null;

/* ---------- Estado guardado na aba (sessionStorage pode falhar: sempre dentro de try/catch) ---------- */

function lerSessao(chave) {
    try { return sessionStorage.getItem(chave); } catch (e) { return null; }
}

function gravarSessao(chave, valor) {
    try { sessionStorage.setItem(chave, valor); } catch (e) { /* sem armazenamento: segue sem guardar */ }
}

const versao = () => $("versao").value;           // "1" ou "2"
const chaveApi = () => $("chave").value.trim();

/* ---------- Erro padronizado das chamadas ---------- */

class ErroApi extends Error {
    constructor({ status = 0, corpo = null, retryAfter = null, rede = false }) {
        super(rede ? "Falha de rede" : `HTTP ${status}`);
        this.status = status;
        this.corpo = corpo;           // ApiError devolvido pela API (timestamp, status, erro, mensagem, campos)
        this.retryAfter = retryAfter; // segundos (header Retry-After do 429)
        this.rede = rede;             // true = o navegador nem chegou a ler a resposta (rede ou CORS)
    }
}

/* ---------- Chamada à API ---------- */

async function chamar(url, { metodo = "GET", corpo = null, idempotencyKey = null } = {}) {
    const v2 = versao() === "2";
    // A versão escolhida vai em TODAS as requisições. Na v1 não há chave de API nem idempotência.
    const headers = { "X-API-Version": versao() };
    if (metodo !== "GET" && v2 && chaveApi()) {
        headers["X-API-Key"] = chaveApi();            // GET é público: a chave só vai em escritas
    }
    if (corpo) {
        headers["Content-Type"] = "application/json";
    }
    if (idempotencyKey && v2) {
        headers["X-Idempotency-Key"] = idempotencyKey;
    }

    let resposta;
    try {
        resposta = await fetch(url, {
            method: metodo,
            headers,
            body: corpo ? JSON.stringify(corpo) : undefined,
        });
    } catch (e) {
        throw new ErroApi({ rede: true });
    }

    atualizarLimite(resposta);

    const texto = await resposta.text();
    let dados = null;
    try { dados = texto ? JSON.parse(texto) : null; } catch (e) { /* corpo não era JSON */ }

    if (!resposta.ok) {
        throw new ErroApi({ status: resposta.status, corpo: dados, retryAfter: resposta.headers.get("Retry-After") });
    }
    return { dados, resposta };
}

// Só dá para ler estes headers porque a API os "expõe" no CORS (Access-Control-Expose-Headers)
function atualizarLimite(resposta) {
    const limite = resposta.headers.get("X-RateLimit-Limit");
    const restantes = resposta.headers.get("X-RateLimit-Remaining");
    const reinicia = resposta.headers.get("X-RateLimit-Reset");
    $("limite").textContent = limite
        ? `Limite da API: ${restantes} de ${limite} requisições restantes (renova em ${reinicia} s).`
        : "";
}

/* ---------- Mensagens amigáveis ---------- */

const ROTULOS = {
    nome: "Nome do time",
    sigla: "Sigla",
    dataFundacao: "Data de fundação",
    estadio: "Estádio",
    "estadio.nome": "Nome do estádio",
    "estadio.capacidade": "Capacidade",
};

function mostrarAviso(tipo, texto, itens = []) {
    const caixa = $("aviso");
    caixa.className = `aviso ${tipo}`;
    caixa.textContent = texto;
    if (itens.length) {
        const lista = document.createElement("ul");
        itens.forEach((item) => {
            const li = document.createElement("li");
            li.textContent = item;
            lista.appendChild(li);
        });
        caixa.appendChild(lista);
    }
}

function limparAviso() {
    $("aviso").className = "aviso oculto";
    $("aviso").textContent = "";
}

function mostrarErro(e) {
    if (!(e instanceof ErroApi)) {
        mostrarAviso("erro", "Erro inesperado no navegador. Veja o console para detalhes.");
        console.error(e);
        return;
    }
    if (e.rede) {
        if (versao() === "1") {
            mostrarAviso("info", "A versão 1 não envia cabeçalhos CORS, então o navegador bloqueou a resposta. "
                + "Isso é esperado: a v1 é a API original, sem CORS. Troque para a V2.");
        } else {
            mostrarAviso("erro", "Não foi possível falar com a API. Confira se o serviço está no ar (no plano gratuito "
                + "pode levar cerca de 1 minuto para acordar) e se o endereço desta página está em CORS_ALLOWED_ORIGINS.");
        }
        return;
    }

    const mensagemApi = e.corpo && e.corpo.mensagem ? e.corpo.mensagem : "";
    switch (e.status) {
        case 400: {
            const campos = e.corpo && e.corpo.campos ? Object.entries(e.corpo.campos) : [];
            mostrarAviso("erro", "Dados inválidos. Corrija e tente de novo:",
                campos.length ? campos.map(([campo, msg]) => `${ROTULOS[campo] || campo}: ${msg}`) : [mensagemApi]);
            break;
        }
        case 401:
            mostrarAviso("erro", chaveApi()
                ? "Chave de API inválida ou revogada. Confira a chave digitada no topo da página."
                : "Esta ação exige uma chave de API. Digite a sua no campo \"Chave de API\" (versão 2).");
            break;
        case 409:
            mostrarAviso("erro", `Conflito: ${mensagemApi || "já existe uma requisição em processamento com essa chave."}`);
            break;
        case 422:
            mostrarAviso("erro", `Chave de idempotência reutilizada com outro conteúdo. ${mensagemApi}`);
            break;
        case 429:
            mostrarAviso("erro", `Muitas requisições em pouco tempo. Aguarde ${e.retryAfter || "alguns"} segundos e tente de novo.`);
            break;
        default:
            mostrarAviso("erro", `Erro ${e.status}: ${mensagemApi || "a API não conseguiu atender o pedido."}`);
    }
}

/* ---------- Lista de times (segue os links HATEOAS) ---------- */

const urlPrimeiraPagina = () => `${API}/times?page=0&size=${TAMANHO_PAGINA}&sort=id,asc`;

// Só segue links que apontam para a própria API (não manda requisição para endereços de terceiros)
function mesmaOrigem(url) {
    try { return new URL(url).origin === new URL(API).origin; } catch (e) { return false; }
}

function formatarData(iso) {
    const partes = String(iso || "").split("-");
    return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : "";
}

function celula(texto, classe) {
    const td = document.createElement("td");
    td.textContent = texto;
    if (classe) td.className = classe;
    return td;
}

function mostrarPagina(dados) {
    // A lista vem em _embedded (HAL). O nome da chave (timeList) não importa: pega a primeira lista
    const times = dados._embedded ? (Object.values(dados._embedded)[0] || []) : [];
    const corpo = $("tabela");
    corpo.replaceChildren();

    if (times.length === 0) {
        const linha = document.createElement("tr");
        const vazio = celula("Nenhum time cadastrado ainda.", "vazio");
        vazio.colSpan = 6;
        linha.appendChild(vazio);
        corpo.appendChild(linha);
    }
    times.forEach((t) => {
        const linha = document.createElement("tr");
        linha.append(
            celula(t.id),
            celula(t.nome),
            celula(t.sigla),
            celula(formatarData(t.dataFundacao)),
            celula(t.estadio ? t.estadio.nome : ""),
            celula(t.estadio && t.estadio.capacidade != null ? t.estadio.capacidade.toLocaleString("pt-BR") : ""));
        corpo.appendChild(linha);
    });

    const pag = dados.page || {};
    linksPagina = dados._links || {};
    $("resumo").textContent = `${pag.totalElements ?? times.length} time(s) cadastrado(s).`;
    $("pagina").textContent = pag.totalPages ? `Página ${pag.number + 1} de ${pag.totalPages}` : "Sem páginas";
    $("anterior").disabled = !linksPagina.prev;
    $("proxima").disabled = !linksPagina.next;
}

async function carregar(url) {
    if (!mesmaOrigem(url)) {
        mostrarAviso("erro", "Link recusado: ele não aponta para a API configurada.");
        return;
    }
    $("resumo").textContent = "Carregando...";
    try {
        const { dados } = await chamar(url);
        mostrarPagina(dados);
    } catch (e) {
        $("resumo").textContent = "Não foi possível carregar os times.";
        $("tabela").replaceChildren();
        $("anterior").disabled = true;
        $("proxima").disabled = true;
        $("pagina").textContent = " ";
        mostrarErro(e);
    }
}

/* ---------- Cadastro com idempotência ---------- */

function novaChaveIdempotencia() {
    // crypto.randomUUID só existe em páginas seguras (https ou localhost)
    return typeof crypto.randomUUID === "function"
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function atualizarBotaoReenviar() {
    $("reenviar").disabled = !(ultimoCadastro && versao() === "2");
}

async function enviarTime(corpo, idempotencyKey) {
    limparAviso();
    $("cadastrar").disabled = true;
    $("reenviar").disabled = true;
    try {
        const { dados, resposta } = await chamar(`${API}/times`, { metodo: "POST", corpo, idempotencyKey });
        // Se a API devolveu a resposta guardada, ela avisa neste header
        const repetida = resposta.headers.get("Idempotent-Replayed") === "true";
        ultimoCadastro = { corpo, idempotencyKey };
        $("info-idempotencia").textContent = versao() === "2"
            ? `Chave de idempotência usada (X-Idempotency-Key): ${idempotencyKey}`
            : "Versão 1: sem idempotência (cada envio cria um time novo).";
        await carregar(urlPrimeiraPagina());
        if (repetida) {
            mostrarAviso("info", `Requisição repetida: a API reconheceu a chave de idempotência e devolveu a resposta guardada `
                + `(time id ${dados.id}). Nada foi criado de novo.`);
        } else {
            mostrarAviso("ok", `Time "${dados.nome}" cadastrado (id ${dados.id}).`);
        }
    } catch (e) {
        mostrarErro(e);
    } finally {
        $("cadastrar").disabled = false;
        atualizarBotaoReenviar();
    }
}

/* ---------- Versão e chave ---------- */

function atualizarVersao() {
    const v1 = versao() === "1";
    $("versao").classList.toggle("v1", v1);
    $("versao-dica").textContent = v1
        ? "Original: sem chave de API, rate limit, CORS e idempotência. O navegador bloqueia chamadas de outro site."
        : "Envia a chave de API e a chave de idempotência; rate limit e CORS ativos.";
    $("chave").disabled = v1;
    atualizarBotaoReenviar();
}

function iniciar() {
    // Valores guardados nesta aba (padrão: V2)
    const salvaVersao = lerSessao("versaoApi");
    $("versao").value = salvaVersao === "1" ? "1" : "2";
    $("chave").value = lerSessao("chaveApi") || "";
    // Não deixa escolher data futura (a API também recusa)
    const hoje = new Date();
    $("fundacao").max = `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, "0")}-${String(hoje.getDate()).padStart(2, "0")}`;
    atualizarVersao();

    $("versao").addEventListener("change", () => {
        gravarSessao("versaoApi", versao());
        atualizarVersao();
        limparAviso();
        carregar(urlPrimeiraPagina());
    });

    $("chave").addEventListener("input", () => gravarSessao("chaveApi", $("chave").value));
    $("limpar-chave").addEventListener("click", () => {
        $("chave").value = "";
        gravarSessao("chaveApi", "");
        $("chave").focus();
    });

    $("sigla").addEventListener("input", (ev) => { ev.target.value = ev.target.value.toUpperCase(); });

    $("recarregar").addEventListener("click", () => { limparAviso(); carregar(urlPrimeiraPagina()); });
    $("anterior").addEventListener("click", () => { if (linksPagina.prev) carregar(linksPagina.prev.href); });
    $("proxima").addEventListener("click", () => { if (linksPagina.next) carregar(linksPagina.next.href); });

    $("form-time").addEventListener("submit", (ev) => {
        ev.preventDefault();
        if (!ev.target.checkValidity()) {
            ev.target.reportValidity();
            return;
        }
        const corpo = {
            nome: $("nome").value.trim(),
            sigla: $("sigla").value.trim().toUpperCase(),
            dataFundacao: $("fundacao").value,
            estadio: { nome: $("estadio").value.trim(), capacidade: Number($("capacidade").value) },
        };
        // Uma chave NOVA a cada cadastro: ela identifica esta operação
        enviarTime(corpo, novaChaveIdempotencia());
    });

    $("reenviar").addEventListener("click", () => {
        if (ultimoCadastro) enviarTime(ultimoCadastro.corpo, ultimoCadastro.idempotencyKey); // MESMA chave
    });

    carregar(urlPrimeiraPagina());
}

iniciar();
