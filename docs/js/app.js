// Casca da aplicação: menu, rotas (#/times...), versão da API, chave, medidor de limite e registro de chamadas.

import {
    API, ErroApi, eventoChave, eventoLimite, eventoRegistro, getChave, getSituacaoChave, getVersao, podeEscrever, registro, repetir,
    setChave, setVersao, verificarChave,
} from "./api.js";
import { mostrarErro } from "./erros.js";
import { montarLista } from "./lista.js";
import { montarPainel } from "./painel.js";
import { RECURSOS } from "./recursos.js";
import { estadoVazio, h, icone, limpar, toast } from "./ui.js";

const $ = (id) => document.getElementById(id);

const ROTAS = [
    { chave: "", rotulo: "Painel", icone: "painel" },
    { grupo: "Cadastros" },
    { chave: "times", rotulo: "Times", icone: "time" },
    { chave: "estadios", rotulo: "Estádios", icone: "estadio" },
    { chave: "jogadores", rotulo: "Jogadores", icone: "jogador" },
    { chave: "campeonatos", rotulo: "Campeonatos", icone: "campeonato" },
    { chave: "partidas", rotulo: "Partidas", icone: "partida" },
    { grupo: "Administração" },
    { chave: "chaves", rotulo: "Chaves de API", icone: "chave" },
];

/* ---------- Menu ---------- */

function montarMenu() {
    limpar($("nav")).append(...ROTAS.map((r) => (r.grupo
        ? h("p", { class: "nav-titulo" }, r.grupo)
        : h("a", { href: `#/${r.chave}`, dataset: { rota: r.chave } }, icone(r.icone), r.rotulo))));
}

/* ---------- Rotas ---------- */

function rotaAtual() {
    const chave = location.hash.replace(/^#\/?/, "").split("?")[0];
    return ROTAS.some((r) => r.chave === chave) ? chave : "";
}

function rotear() {
    const chave = rotaAtual();
    const conteudo = limpar($("conteudo"));
    document.querySelectorAll("#nav a").forEach((a) => {
        if (a.dataset.rota === chave) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
    });
    const titulo = chave ? RECURSOS[chave].titulo : "Painel";
    document.title = `${titulo} · Campeonato FC`;

    if (chave === "") {
        montarPainel(conteudo);
    } else if (chave === "chaves" && (getVersao() === "1" || !getChave())) {
        // A área de chaves exige chave até para ler: sem ela, nem tentamos
        conteudo.append(h("header", { class: "vista-cab" }, h("div", {}, h("p", { class: "kicker" }, "Acesso"), h("h1", {}, "Chaves de API"))),
            estadoVazio({
                titulo: "Área restrita",
                texto: getVersao() === "1"
                    ? "A versão 1 é a API original e não tem chaves de API. Volte para a V2."
                    : "Esta área exige uma chave de API (até para listar). Digite a sua no campo \"Chave de API\" no topo da página.",
                acao: getVersao() === "2" ? h("button", { type: "button", class: "botao primario", onClick: () => $("chave").focus() }, icone("chave"), "Informar chave") : null,
            }));
    } else {
        montarLista(RECURSOS[chave], conteudo);
    }
    window.scrollTo({ top: 0 });
}

/* ---------- Versão da API ---------- */

function atualizarVersao() {
    const v = getVersao();
    document.querySelectorAll(".seg button").forEach((b) => b.setAttribute("aria-checked", String(b.dataset.versao === v)));
    $("faixa-v1").classList.toggle("oculto", v !== "1");
    $("chave").disabled = v === "1";
    atualizarChave();
}

/* ---------- Chave de API ---------- */

function atualizarChave() {
    const v1 = getVersao() === "1";
    const situacao = getSituacaoChave();
    const caixa = document.querySelector(".campo-chave");
    const textos = {
        sem: ["Somente leitura: informe uma chave para editar dados", ""],
        verificando: ["Verificando a chave…", ""],
        valida: ["Chave válida: edição liberada", "ok"],
        invalida: ["Chave recusada pela API", "erro"],
        erro: ["Não foi possível verificar a chave agora", "erro"],
    };
    const [texto, classe] = v1 ? ["Na V1 não existe chave de API", ""] : textos[situacao];
    $("chave-estado").textContent = texto;
    $("chave-estado").className = `chave-estado ${classe}`.trim();
    caixa.dataset.estado = v1 ? "" : situacao;
    limpar($("chave-icone")).append(icone(situacao === "valida" && !v1 ? "cadeado-aberto" : "cadeado"));
    document.body.dataset.escrita = podeEscrever() ? "sim" : "nao";
}

let adiamento = null;
function aoDigitarChave() {
    setChave($("chave").value);
    atualizarChave();
    clearTimeout(adiamento);
    // Espera o usuário parar de digitar antes de perguntar à API se a chave vale
    adiamento = setTimeout(() => verificarChave(), 700);
}

/* ---------- Medidor do limite de requisições (X-RateLimit-*) ---------- */

function atualizarLimite(info) {
    $("grupo-limite").hidden = !info;
    if (!info) return;
    const fracao = info.limite ? info.restantes / info.limite : 0;
    $("medidor-barra").style.width = `${Math.max(2, fracao * 100)}%`;
    $("medidor-barra").className = `medidor-barra ${fracao < 0.2 ? "baixo" : fracao < 0.5 ? "medio" : ""}`.trim();
    $("medidor-texto").textContent = `${info.restantes} de ${info.limite} · renova em ${info.reinicia}s`;
}

/* ---------- Registro de chamadas ---------- */

function desenharRegistro() {
    $("contador").textContent = String(registro.length);
    const lista = limpar($("chamadas"));
    if (!registro.length) {
        lista.append(h("li", { class: "vazio-registro" }, "Nenhuma chamada ainda."));
        return;
    }
    registro.forEach((c) => {
        const faixa = c.status === 0 ? "s0" : `s${String(c.status)[0]}`;
        const podeRepetir = c.metodo === "POST" && c.idempotencyKey && c.status >= 200 && c.status < 300;
        lista.append(h("li", {},
            h("div", { class: "linha1" },
                h("span", { class: `chip metodo ${c.metodo.toLowerCase()}` }, c.metodo),
                h("span", { class: `status-http ${faixa}` }, c.status === 0 ? "sem resposta" : c.status),
                c.replay ? h("span", { class: "chip replay" }, "Idempotent-Replayed") : null,
                c.idempotencyKey ? h("span", { class: "chip", title: `X-Idempotency-Key: ${c.idempotencyKey}` }, "idempotente") : null),
            h("div", { class: "caminho" }, decodeURIComponent(c.caminho)),
            h("div", { class: "linha2" },
                h("span", {}, `${c.quando.toLocaleTimeString("pt-BR")} · ${c.ms} ms`),
                podeRepetir ? h("button", { type: "button", class: "btn-mini", onClick: () => repetirChamada(c) }, icone("repetir"), "Repetir") : null)));
    });
}

async function repetirChamada(entrada) {
    try {
        const { replay } = await repetir(entrada);
        toast(replay ? "info" : "ok", replay
            ? "Mesma chave de idempotência: a API devolveu a resposta guardada (Idempotent-Replayed) e nada foi criado de novo."
            : "A API processou a requisição de novo.");
    } catch (e) {
        mostrarErro(e);
    }
}

function alternarRegistro(abrir) {
    const mostrar = abrir === undefined ? $("gaveta").hidden : abrir;
    $("gaveta").hidden = !mostrar;
    $("btn-registro").setAttribute("aria-expanded", String(mostrar));
}

/* ---------- Início ---------- */

function iniciar() {
    $("api-host").textContent = new URL(API).host;
    $("icone-registro").replaceWith(icone("lista"));
    $("fechar-registro").append(icone("fechar"));
    $("chave").value = getChave();
    montarMenu();
    atualizarVersao();
    desenharRegistro();

    document.querySelectorAll(".seg button").forEach((b) => b.addEventListener("click", () => {
        if (b.dataset.versao === getVersao()) return;
        setVersao(b.dataset.versao);
        setChave($("chave").value); // reavalia a situação da chave na nova versão
        atualizarVersao();
        verificarChave();
        rotear();
    }));

    $("chave").addEventListener("input", aoDigitarChave);
    $("limpar-chave").addEventListener("click", () => {
        $("chave").value = "";
        aoDigitarChave();
        $("chave").focus();
    });
    $("btn-registro").addEventListener("click", () => alternarRegistro());
    $("fechar-registro").addEventListener("click", () => alternarRegistro(false));

    eventoRegistro.ouvir(desenharRegistro);
    eventoLimite.ouvir(atualizarLimite);
    let situacaoAnterior = getSituacaoChave();
    eventoChave.ouvir((situacao) => {
        atualizarChave();
        // Se a área de chaves está aberta e a situação da chave mudou, redesenha (libera ou bloqueia a tela)
        if (rotaAtual() === "chaves" && situacao !== situacaoAnterior && ["valida", "invalida"].includes(situacao)) rotear();
        situacaoAnterior = situacao;
    });

    window.addEventListener("hashchange", rotear);
    rotear();
    if (getVersao() === "2" && getChave()) {
        setChave(getChave());
        verificarChave();
    }
}

iniciar();
