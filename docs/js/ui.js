// Blocos de interface: criação de elementos, ícones, escudos, avisos e diálogos.
// Segurança: nada aqui usa innerHTML com dados da API. Textos entram sempre como texto (nós de texto / textContent).

const PROPRIEDADES = new Set(["disabled", "required", "checked", "selected", "hidden", "multiple", "open", "value"]);

/** Cria um elemento: h("button", { class: "x", onClick: fn }, "texto", outroNo) */
export function h(tag, props = {}, ...filhos) {
    const el = document.createElement(tag);
    for (const [chave, valor] of Object.entries(props || {})) {
        if (valor === null || valor === undefined || valor === false) continue;
        if (chave === "class") el.className = valor;
        else if (chave === "dataset") Object.assign(el.dataset, valor);
        else if (chave.startsWith("on") && typeof valor === "function") el.addEventListener(chave.slice(2).toLowerCase(), valor);
        else if (PROPRIEDADES.has(chave)) el[chave] = valor;
        else el.setAttribute(chave, valor === true ? "" : String(valor));
    }
    anexar(el, filhos);
    return el;
}

function anexar(pai, filhos) {
    for (const filho of filhos.flat(Infinity)) {
        if (filho === null || filho === undefined || filho === false) continue;
        pai.append(filho instanceof Node ? filho : document.createTextNode(String(filho)));
    }
}

export function limpar(el) {
    el.replaceChildren();
    return el;
}

/* ---------- Ícones (traços simples; as strings abaixo são constantes do código) ---------- */

const ICONES = {
    painel: '<rect x="3" y="3" width="7.5" height="7.5" rx="1"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="1"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="1"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1"/>',
    time: '<path d="M12 3l7 2.5v5.5c0 5-3.2 8.4-7 10-3.8-1.6-7-5-7-10V5.5z"/><path d="M12 8l2.6 1.9-1 3.1h-3.2l-1-3.1z"/>',
    estadio: '<ellipse cx="12" cy="12" rx="9.5" ry="6"/><ellipse cx="12" cy="12" rx="4.5" ry="2.4"/><path d="M12 6v-2M12 20v-2"/>',
    jogador: '<circle cx="12" cy="7.5" r="3.5"/><path d="M5 21c0-4.4 3-7 7-7s7 2.6 7 7"/>',
    campeonato: '<path d="M8 4h8v5a4 4 0 01-8 0z"/><path d="M8 6H5v1.5A3 3 0 008 10.5M16 6h3v1.5a3 3 0 01-3 3M12 13v4M8.5 20.5h7M10 17h4"/>',
    partida: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
    chave: '<circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M16 7l3 3M14 9l2 2"/>',
    mais: '<path d="M12 5v14M5 12h14"/>',
    editar: '<path d="M4 20h4L19.5 8.5a2.1 2.1 0 00-3-3L5 17z"/><path d="M14 7l3 3"/>',
    lixeira: '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5"/>',
    busca: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
    cadeado: '<rect x="5" y="11" width="14" height="9.5" rx="2"/><path d="M8 11V8a4 4 0 018 0v3"/>',
    "cadeado-aberto": '<rect x="5" y="11" width="14" height="9.5" rx="2"/><path d="M8 11V8a4 4 0 017.4-2"/>',
    "seta-esq": '<path d="M15 5l-7 7 7 7"/>',
    "seta-dir": '<path d="M9 5l7 7-7 7"/>',
    "dupla-esq": '<path d="M12 5l-7 7 7 7M19 5l-7 7 7 7"/>',
    "dupla-dir": '<path d="M5 5l7 7-7 7M12 5l7 7-7 7"/>',
    fechar: '<path d="M6 6l12 12M18 6L6 18"/>',
    copiar: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 012-2h9"/>',
    repetir: '<path d="M20 11a8 8 0 10-2.4 5.7M20 4v7h-7"/>',
    lista: '<path d="M4 6h16M4 12h16M4 18h10"/>',
    placar: '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="M12 6v12M7 12h.01M17 12h.01"/>',
    vinculo: '<path d="M10 14a4 4 0 005.6 0l3-3a4 4 0 00-5.6-5.6l-1 1M14 10a4 4 0 00-5.6 0l-3 3a4 4 0 005.6 5.6l1-1"/>',
    usuarios: '<circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.6 2.6-5.5 6-5.5s6 1.9 6 5.5M16 5.2a3.2 3.2 0 010 6M18 14.8c2 .6 3.4 2.2 3.4 5"/>',
};

export function icone(nome, tamanho) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("fill", "none");
    svg.setAttribute("stroke", "currentColor");
    svg.setAttribute("stroke-width", "1.8");
    svg.setAttribute("stroke-linecap", "round");
    svg.setAttribute("stroke-linejoin", "round");
    svg.setAttribute("aria-hidden", "true");
    if (tamanho) {
        svg.setAttribute("width", tamanho);
        svg.setAttribute("height", tamanho);
    }
    svg.innerHTML = ICONES[nome] || ""; // constante do código, nunca dado da API
    return svg;
}

/* ---------- Escudo, chips e formatação ---------- */

// Cor de 0 a 359 calculada sobre a sigla. A mistura (FNV-1a) espalha bem: siglas parecidas (CNB, CNC) ficam com cores bem diferentes.
export function corDoEscudo(sigla) {
    let hash = 2166136261;
    for (const c of String(sigla || "?").slice(0, 3).toUpperCase()) {
        hash ^= c.charCodeAt(0);
        hash = Math.imul(hash, 16777619);
    }
    return (hash >>> 0) % 360;
}

/** Escudo: círculo com a sigla. A cor sai da sigla, então cada time tem a sua. */
export function escudo(sigla, tamanho = "") {
    const texto = String(sigla || "?").slice(0, 3).toUpperCase();
    const el = h("span", { class: `escudo ${tamanho}`.trim(), title: texto }, texto);
    el.style.setProperty("--h", String(corDoEscudo(texto)));
    return el;
}

export const chip = (texto, classe = "") => h("span", { class: `chip ${classe}`.trim() }, texto);

export function fmtData(iso) {
    const partes = String(iso || "").slice(0, 10).split("-");
    return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : "";
}

export function fmtDataHora(iso) {
    const texto = String(iso || "");
    return texto.length >= 16 ? `${fmtData(texto)} ${texto.slice(11, 16)}` : fmtData(texto);
}

export const fmtNumero = (n) => (n === null || n === undefined ? "" : Number(n).toLocaleString("pt-BR"));

/* ---------- Avisos rápidos ---------- */

export function toast(tipo, texto, { itens = [], acao = null, duracao = 5200 } = {}) {
    const area = document.getElementById("toasts");
    const caixa = h("div", { class: `toast ${tipo}`, role: tipo === "erro" ? "alert" : "status" },
        h("div", { class: "toast-texto" }, texto, itens.length ? h("ul", {}, itens.map((i) => h("li", {}, i))) : null),
        acao ? h("button", { type: "button", onClick: () => { acao.fn(); remover(); } }, acao.rotulo) : null);
    function remover() { caixa.remove(); }
    area.append(caixa);
    setTimeout(remover, duracao);
    return caixa;
}

/* ---------- Diálogos ---------- */

/** Abre um diálogo modal (<dialog>). Devolve { fechar, el }. ESC e clique fora também fecham. */
export function abrirDialogo({ titulo, kicker = "", conteudo, rodape = null, larga = false, aoFechar = null }) {
    const el = h("dialog", { class: `dialogo ${larga ? "larga" : ""}`.trim() });
    const botaoFechar = h("button", { type: "button", class: "btn-icone", "aria-label": "Fechar", onClick: () => fechar() }, icone("fechar"));
    el.append(h("div", { class: "dialogo-corpo-total" },
        h("header", { class: "dialogo-cab" },
            h("div", {}, kicker ? h("p", { class: "kicker" }, kicker) : null, h("h2", {}, titulo)),
            botaoFechar),
        h("div", { class: "dialogo-conteudo" }, conteudo),
        rodape ? h("footer", { class: "dialogo-rodape" }, rodape) : null));
    el.addEventListener("click", (ev) => { if (ev.target === el) fechar(); });
    el.addEventListener("close", () => { el.remove(); if (aoFechar) aoFechar(); });
    document.body.append(el);
    el.showModal();
    function fechar() { if (el.open) el.close(); }
    return { fechar, el };
}

/** Confirmação: devolve uma Promise<boolean>. */
export function confirmar({ titulo, texto, rotulo = "Confirmar", perigo = false }) {
    return new Promise((resolver) => {
        let respondeu = false;
        const responder = (valor) => { respondeu = true; resolver(valor); dialogo.fechar(); };
        const dialogo = abrirDialogo({
            titulo, kicker: "Confirmação", conteudo: h("p", {}, texto),
            rodape: [
                h("button", { type: "button", class: "botao", onClick: () => responder(false) }, "Cancelar"),
                h("button", { type: "button", class: `botao ${perigo ? "perigo" : "primario"}`, onClick: () => responder(true) }, rotulo),
            ],
            aoFechar: () => { if (!respondeu) resolver(false); },
        });
    });
}

/* ---------- Estado vazio e de erro ---------- */

const CAMPO_SVG = '<rect x="2" y="2" width="116" height="72" rx="3" fill="none" stroke="currentColor" stroke-width="2"/><path d="M60 2v72" stroke="currentColor" stroke-width="2"/><circle cx="60" cy="38" r="12" fill="none" stroke="currentColor" stroke-width="2"/><rect x="2" y="22" width="18" height="32" fill="none" stroke="currentColor" stroke-width="2"/><rect x="100" y="22" width="18" height="32" fill="none" stroke="currentColor" stroke-width="2"/>';

export function estadoVazio({ titulo, texto, erro = false, acao = null }) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 120 76");
    svg.setAttribute("aria-hidden", "true");
    svg.innerHTML = CAMPO_SVG; // constante do código
    return h("div", { class: `vazio ${erro ? "erro" : ""}`.trim() }, svg, h("h3", {}, titulo), h("p", {}, texto), acao);
}

/** Escreve o valor em um caminho "a.b.c" dentro de um objeto, criando os níveis que faltarem. */
export function definirCaminho(obj, caminho, valor) {
    const partes = caminho.split(".");
    let atual = obj;
    partes.slice(0, -1).forEach((p) => { atual = atual[p] = atual[p] || {}; });
    atual[partes[partes.length - 1]] = valor;
}

export function lerCaminho(obj, caminho) {
    return caminho.split(".").reduce((o, p) => (o === null || o === undefined ? undefined : o[p]), obj);
}
