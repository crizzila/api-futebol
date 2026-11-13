// Descrição de cada tela: campos do formulário, colunas, cartões e ações especiais.
// O motor genérico (lista.js) usa estas descrições para listar, buscar, criar, editar e excluir.

import { API, chamar, extrairLista, getChave, linkDe, listarTudo } from "./api.js";
import { mostrarErro } from "./erros.js";
import { abrirFormulario } from "./formulario.js";
import {
    abrirDialogo, chip, corDoEscudo, escudo, estadoVazio, fmtData, fmtDataHora, fmtNumero, h, icone, limpar, toast,
} from "./ui.js";

/* ---------- Listas de apoio para os campos de seleção (com cache simples) ---------- */

const cache = {};
const catalogo = (nome, caminho) => () => {
    if (!cache[nome]) cache[nome] = listarTudo(caminho).catch((e) => { delete cache[nome]; throw e; });
    return cache[nome];
};
export const todosTimes = catalogo("times", "/times");
export const todosEstadios = catalogo("estadios", "/estadios");
export const todosCampeonatos = catalogo("campeonatos", "/campeonatos");
// Depois de criar, editar ou excluir, as listas de apoio precisam ser buscadas de novo
export const invalidarCatalogo = () => Object.keys(cache).forEach((k) => delete cache[k]);

const POSICOES = [
    { valor: "GOLEIRO", rotulo: "Goleiro" }, { valor: "ZAGUEIRO", rotulo: "Zagueiro" },
    { valor: "LATERAL_ESQUERDO", rotulo: "Lateral esquerdo" }, { valor: "LATERAL_DIREITO", rotulo: "Lateral direito" },
    { valor: "VOLANTE", rotulo: "Volante" }, { valor: "MEIA", rotulo: "Meia" }, { valor: "ATACANTE", rotulo: "Atacante" },
];
const STATUS = [
    { valor: "AGENDADA", rotulo: "Agendada" }, { valor: "EM_ANDAMENTO", rotulo: "Em andamento" },
    { valor: "FINALIZADA", rotulo: "Finalizada" }, { valor: "CANCELADA", rotulo: "Cancelada" },
];
const rotuloDe = (lista, valor) => (lista.find((o) => o.valor === valor) || { rotulo: valor }).rotulo;
const classePosicao = (p) => ({ GOLEIRO: "goleiro", ZAGUEIRO: "defesa", LATERAL_ESQUERDO: "defesa", LATERAL_DIREITO: "defesa", VOLANTE: "meio", MEIA: "meio", ATACANTE: "ataque" })[p] || "";
const comoOpcoesDeTimes = (times) => times.map((t) => ({ valor: t.id, rotulo: `${t.nome} (${t.sigla})` }));

// Data de hoje no fuso do usuário (yyyy-mm-dd): usada como limite máximo da data de fundação
const hoje = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

/* ==========================================================================
   TIMES
   ========================================================================== */

function jogadoresDoTime(time) {
    const conteudo = h("div", {}, h("div", { class: "esqueleto" }));
    abrirDialogo({ titulo: time.nome, kicker: "Jogadores do time", conteudo });
    chamar(`${linkDe(time, "jogadores")}?size=100&sort=nome,asc`).then(({ dados }) => {
        const jogadores = extrairLista(dados);
        limpar(conteudo).append(jogadores.length
            ? h("ul", { class: "lista-simples" }, jogadores.map((j) => h("li", {},
                h("span", { class: "grow" }, j.nome), chip(rotuloDe(POSICOES, j.posicao), classePosicao(j.posicao)))))
            : estadoVazio({ titulo: "Elenco vazio", texto: "Este time ainda não tem jogadores. Cadastre na tela Jogadores." }));
    }).catch((e) => { limpar(conteudo); mostrarErro(e); });
}

function trocarEstadio(time, ctx) {
    abrirFormulario({
        titulo: "Trocar estádio", kicker: time.nome, rotuloSalvar: "Vincular estádio",
        campos: [{
            caminho: "estadioId", rotulo: "Estádio", tipo: "selecao", obrigatorio: true, inteira: true,
            ajuda: "Só estádios livres (sem time) podem ser vinculados. Cadastre novos na tela Estádios.",
            opcoes: async () => (await todosEstadios()).map((e) => ({
                valor: e.id, rotulo: `${e.nome} · ${fmtNumero(e.capacidade)} lugares${time.estadio && e.id === time.estadio.id ? " (atual)" : ""}`,
            })),
        }],
        aoSalvar: async (corpo) => {
            await chamar(`${API}/times/${time.id}/estadio/${corpo.estadioId}`, { metodo: "PUT" });
            toast("ok", `Estádio de ${time.nome} atualizado.`);
            invalidarCatalogo();
            ctx.recarregar();
        },
    });
}

const times = {
    rota: "/times", titulo: "Times", kicker: "Clubes", singular: "time", layout: "cartoes", artigoNovo: "um",
    descricao: "Cada clube com seu estádio sede. A navegação entre páginas segue os links que a própria API devolve (HATEOAS).",
    nomeItem: (t) => t.nome,
    aoMudar: invalidarCatalogo,
    textoExcluir: (t) => `Excluir "${t.nome}" também remove o estádio e os jogadores do time. Times que já têm partidas não podem ser excluídos.`,
    buscas: [{ rotulo: "Nome", caminho: "/buscar-por-nome", param: "nome", placeholder: "Parte do nome do time" }],
    campos: [
        { caminho: "nome", rotulo: "Nome do time", obrigatorio: true, minlength: 3, maxlength: 50, placeholder: "Palmeiras", inteira: true },
        { caminho: "sigla", rotulo: "Sigla (3 letras)", obrigatorio: true, maxlength: 3, pattern: "[A-Za-z]{3}", placeholder: "PAL", maiusculas: true },
        { caminho: "dataFundacao", rotulo: "Data de fundação", tipo: "data", obrigatorio: true, max: hoje() },
        { grupo: "Estádio sede" },
        { caminho: "estadio.nome", rotulo: "Nome do estádio", obrigatorio: true, minlength: 3, maxlength: 100, placeholder: "Allianz Parque" },
        { caminho: "estadio.capacidade", rotulo: "Capacidade (mín. 1000)", tipo: "numero", obrigatorio: true, min: 1000, passo: 1, placeholder: "43713" },
    ],
    acoes: (t, ctx) => [
        { rotulo: "Jogadores", icone: "jogador", fn: () => jogadoresDoTime(t) },
        { rotulo: "Estádio", icone: "vinculo", escrita: true, fn: () => trocarEstadio(t, ctx) },
    ],
    renderCartao(t, botoes) {
        const el = h("article", { class: "cartao-item" },
            h("div", { class: "cartao-topo" },
                escudo(t.sigla, "grande"),
                h("div", {}, h("h3", {}, t.nome), h("p", { class: "cartao-meta" }, `${t.sigla} · fundado em ${fmtData(t.dataFundacao)}`))),
            h("dl", { class: "cartao-dados" },
                h("dt", {}, "Estádio"), h("dd", {}, t.estadio ? t.estadio.nome : "—"),
                h("dt", {}, "Capacidade"), h("dd", {}, t.estadio ? fmtNumero(t.estadio.capacidade) : "—")),
            botoes(t, "mini"));
        el.style.setProperty("--h", String(corDoEscudo(t.sigla)));
        return el;
    },
};

/* ==========================================================================
   ESTÁDIOS
   ========================================================================== */

const estadios = {
    rota: "/estadios", titulo: "Estádios", kicker: "Arenas", singular: "estádio", layout: "tabela", artigoNovo: "um",
    descricao: "Estádios sede dos times (um estádio para cada time). Estádios que já pertencem a um time não podem ser excluídos.",
    nomeItem: (e) => e.nome,
    aoMudar: invalidarCatalogo,
    buscas: [
        { rotulo: "Nome", caminho: "/buscar-por-nome", param: "nome", placeholder: "Parte do nome do estádio" },
        { rotulo: "Capacidade mínima", caminho: "/buscar-por-capacidade", param: "capacidadeMinima", tipo: "numero", placeholder: "ex.: 40000" },
    ],
    campos: [
        { caminho: "nome", rotulo: "Nome do estádio", obrigatorio: true, minlength: 3, maxlength: 100, placeholder: "Maracanã", inteira: true },
        { caminho: "capacidade", rotulo: "Capacidade (mín. 1000)", tipo: "numero", obrigatorio: true, min: 1000, passo: 1, placeholder: "78000", inteira: true },
    ],
    colunas: [
        { titulo: "ID", classe: "id", render: (e) => e.id },
        { titulo: "Estádio", render: (e) => h("strong", {}, e.nome) },
        {
            titulo: "Capacidade", classe: "num",
            render: (e) => h("div", { class: "barra-cap" },
                h("i", {}, h("b", { style: `width:${Math.min(100, e.capacidade / 1000)}%` })), fmtNumero(e.capacidade)),
        },
        { titulo: "Situação", render: (e) => (linkDe(e, "time") ? chip("Vinculado a um time", "vinculado") : chip("Livre", "livre")) },
    ],
};

/* ==========================================================================
   JOGADORES
   ========================================================================== */

const jogadores = {
    rota: "/jogadores", titulo: "Jogadores", kicker: "Elenco", singular: "jogador", layout: "tabela", artigoNovo: "um",
    descricao: "Atletas vinculados a um time. Ao editar, é possível transferir o jogador para outro time.",
    nomeItem: (j) => j.nome,
    buscas: [
        { rotulo: "Nome", caminho: "/buscar-por-nome", param: "nome", placeholder: "Parte do nome do jogador" },
        { rotulo: "Posição", caminho: "/buscar-por-posicao", param: "posicao", tipo: "selecao", opcoes: POSICOES },
    ],
    campos: [
        { caminho: "nome", rotulo: "Nome do jogador", obrigatorio: true, minlength: 2, maxlength: 80, placeholder: "Weverton", inteira: true },
        { caminho: "posicao", rotulo: "Posição", tipo: "selecao", obrigatorio: true, opcoes: POSICOES },
        {
            caminho: "time.id", rotulo: "Time", tipo: "selecao", obrigatorio: true, valorNumerico: true,
            semOpcoes: "Cadastre um time primeiro", opcoes: async () => comoOpcoesDeTimes(await todosTimes()),
        },
    ],
    colunas: [
        { titulo: "ID", classe: "id", render: (j) => j.id },
        { titulo: "Jogador", render: (j) => h("strong", {}, j.nome) },
        { titulo: "Posição", render: (j) => chip(rotuloDe(POSICOES, j.posicao), classePosicao(j.posicao)) },
        {
            titulo: "Time",
            render: (j) => (j.time ? h("div", { class: "celula-time" }, escudo(j.time.sigla, "mini"), j.time.nome) : "—"),
        },
    ],
};

/* ==========================================================================
   CAMPEONATOS
   ========================================================================== */

function gerenciarTimes(campeonato, ctx) {
    let atual = campeonato;
    const corpo = h("div", {});
    const dialogo = abrirDialogo({
        titulo: campeonato.nome, kicker: "Times participantes", conteudo: corpo, larga: true,
        aoFechar: () => ctx.recarregar(),
    });

    async function atualizar() {
        const { dados } = await chamar(linkDe(atual, "self"));
        atual = dados;
        invalidarCatalogo();
        await desenhar();
    }

    async function desenhar() {
        const participantes = atual.times || [];
        const disponiveis = (await todosTimes()).filter((t) => !participantes.some((p) => p.id === t.id));
        const seletor = h("select", { "aria-label": "Time para adicionar" },
            h("option", { value: "" }, disponiveis.length ? "Escolha um time…" : "Todos os times já participam"),
            disponiveis.map((t) => h("option", { value: t.id }, `${t.nome} (${t.sigla})`)));

        limpar(corpo).append(
            participantes.length
                ? h("ul", { class: "lista-simples" }, participantes.map((t) => h("li", {},
                    escudo(t.sigla), h("span", { class: "grow" }, t.nome, h("small", {}, t.estadio ? t.estadio.nome : "")),
                    h("button", {
                        type: "button", class: "btn-mini perigo acao-escrita", onClick: () => ctx.executar({
                            escrita: true,
                            fn: async () => {
                                await chamar(`${API}/campeonatos/${atual.id}/times/${t.id}`, { metodo: "DELETE" });
                                toast("ok", `${t.nome} saiu do campeonato.`);
                                await atualizar();
                            },
                        }),
                    }, icone("lixeira"), "Remover"))))
                : estadoVazio({ titulo: "Sem times", texto: "Adicione os times que vão disputar este campeonato." }),
            h("div", { class: "linha-adicionar" }, seletor,
                h("button", {
                    type: "button", class: "botao primario acao-escrita", disabled: !disponiveis.length,
                    onClick: () => ctx.executar({
                        escrita: true,
                        fn: async () => {
                            if (!seletor.value) { toast("aviso", "Escolha um time para adicionar."); return; }
                            await chamar(`${API}/campeonatos/${atual.id}/times/${seletor.value}`, { metodo: "POST" });
                            toast("ok", "Time adicionado ao campeonato.");
                            await atualizar();
                        },
                    }),
                }, icone("mais"), "Adicionar")));
    }

    desenhar().catch((e) => { limpar(corpo); mostrarErro(e); dialogo.fechar(); });
}

const campeonatos = {
    rota: "/campeonatos", titulo: "Campeonatos", kicker: "Competições", singular: "campeonato", layout: "cartoes", artigoNovo: "um",
    descricao: "Competições por temporada. Use o botão Times para escolher quem disputa cada uma (só times participantes jogam partidas nela).",
    nomeItem: (c) => c.nome,
    aoMudar: invalidarCatalogo,
    textoExcluir: (c) => `Excluir "${c.nome}"? Campeonatos que já têm partidas não podem ser excluídos.`,
    buscas: [
        { rotulo: "Nome", caminho: "/buscar-por-nome", param: "nome", placeholder: "Parte do nome do campeonato" },
        { rotulo: "Ano", caminho: "/buscar-por-ano", param: "ano", tipo: "numero", placeholder: "ex.: 2026" },
    ],
    campos: [
        { caminho: "nome", rotulo: "Nome do campeonato", obrigatorio: true, minlength: 5, maxlength: 100, placeholder: "Campeonato Brasileiro", inteira: true },
        { caminho: "ano", rotulo: "Ano (mín. 1888)", tipo: "numero", obrigatorio: true, min: 1888, passo: 1, placeholder: String(new Date().getFullYear()), inteira: true },
    ],
    acoes: (c, ctx) => [{ rotulo: "Times", icone: "usuarios", fn: () => gerenciarTimes(c, ctx) }],
    renderCartao(c, botoes) {
        const participantes = c.times || [];
        return h("article", { class: "cartao-item", style: "border-top-color:var(--tinta)" },
            h("div", {}, h("p", { class: "kicker" }, `Temporada ${c.ano}`), h("h3", { style: "margin-top:6px" }, c.nome)),
            h("div", { style: "display:flex;align-items:center;min-height:34px" },
                participantes.length
                    ? [h("div", { class: "pilha-escudos" }, participantes.slice(0, 6).map((t) => escudo(t.sigla))),
                        h("span", { class: "mais-times" }, `${participantes.length} time${participantes.length === 1 ? "" : "s"}`)]
                    : h("span", { class: "cartao-meta" }, "Nenhum time participante ainda")),
            botoes(c, "mini"));
    },
};

/* ==========================================================================
   PARTIDAS
   ========================================================================== */

// Atualização rápida de placar e situação: reaproveita o PUT completo, mudando só estes campos
function registrarPlacar(partida, ctx) {
    abrirFormulario({
        titulo: `${partida.mandante.sigla} x ${partida.visitante.sigla}`, kicker: "Registrar placar", rotuloSalvar: "Salvar placar",
        valores: partida,
        campos: [
            { caminho: "placarMandante", rotulo: `Gols ${partida.mandante.nome}`, tipo: "numero", obrigatorio: true, min: 0, passo: 1 },
            { caminho: "placarVisitante", rotulo: `Gols ${partida.visitante.nome}`, tipo: "numero", obrigatorio: true, min: 0, passo: 1 },
            { caminho: "status", rotulo: "Situação", tipo: "selecao", obrigatorio: true, opcoes: STATUS, inteira: true },
        ],
        aoSalvar: async (corpo) => {
            await chamar(linkDe(partida, "update"), {
                metodo: "PUT",
                corpo: {
                    dataHora: partida.dataHora, status: corpo.status, placarMandante: corpo.placarMandante, placarVisitante: corpo.placarVisitante,
                    mandante: { id: partida.mandante.id }, visitante: { id: partida.visitante.id }, campeonato: { id: partida.campeonato.id },
                },
            });
            toast("ok", "Placar registrado.");
            ctx.recarregar();
        },
    });
}

// Mandante e visitante só podem ser times do campeonato escolhido (regra da API): a lista acompanha o campeonato
const timesDoCampeonato = async (valores) => {
    const campeonato = (await todosCampeonatos()).find((c) => String(c.id) === valores["campeonato"]?.id);
    return campeonato ? comoOpcoesDeTimes(campeonato.times || []) : [];
};

const partidas = {
    rota: "/partidas", titulo: "Partidas", kicker: "Jogos", singular: "partida", layout: "placares", artigoNovo: "uma", ordem: "dataHora,desc",
    rotuloNovo: "Nova partida", formLargo: true,
    descricao: "Jogos entre dois times de um campeonato. Mandante e visitante precisam participar do campeonato escolhido.",
    nomeItem: (p) => `${p.mandante.sigla} x ${p.visitante.sigla}`,
    aoMudar: invalidarCatalogo,
    verboExcluido: "excluída",
    buscas: [{ rotulo: "Situação", caminho: "/buscar-por-status", param: "status", tipo: "selecao", opcoes: STATUS }],
    campos: [
        {
            caminho: "campeonato.id", rotulo: "Campeonato", tipo: "selecao", obrigatorio: true, valorNumerico: true, inteira: true,
            semOpcoes: "Cadastre um campeonato primeiro",
            opcoes: async () => (await todosCampeonatos()).map((c) => ({ valor: c.id, rotulo: `${c.nome} (${c.ano})` })),
        },
        {
            caminho: "mandante.id", rotulo: "Mandante", tipo: "selecao", obrigatorio: true, valorNumerico: true, dependeDe: "campeonato.id",
            semOpcoes: "Escolha um campeonato que tenha times", opcoes: timesDoCampeonato,
        },
        {
            caminho: "visitante.id", rotulo: "Visitante", tipo: "selecao", obrigatorio: true, valorNumerico: true, dependeDe: "campeonato.id",
            semOpcoes: "Escolha um campeonato que tenha times", opcoes: timesDoCampeonato,
        },
        { caminho: "dataHora", rotulo: "Data e hora", tipo: "datahora", obrigatorio: true },
        { caminho: "status", rotulo: "Situação", tipo: "selecao", opcoes: STATUS, valorPadrao: "AGENDADA" },
        { caminho: "placarMandante", rotulo: "Gols do mandante", tipo: "numero", min: 0, passo: 1, valorPadrao: 0 },
        { caminho: "placarVisitante", rotulo: "Gols do visitante", tipo: "numero", min: 0, passo: 1, valorPadrao: 0 },
    ],
    acoes: (p, ctx) => [{ rotulo: "Placar", icone: "placar", escrita: true, fn: () => registrarPlacar(p, ctx) }],
    renderPlacar(p, botoes) {
        const status = String(p.status || "").toLowerCase().replace(/_/g, "-");
        return h("article", { class: "placar" },
            h("div", { class: "placar-topo" },
                chip(rotuloDe(STATUS, p.status), status),
                h("span", { class: "camp" }, p.campeonato ? `${p.campeonato.nome} · ${p.campeonato.ano}` : "")),
            h("div", { class: "placar-corpo" },
                h("div", { class: "placar-time" }, escudo(p.mandante.sigla, "medio"), h("strong", {}, p.mandante.nome)),
                h("div", { class: "placar-numeros" }, h("span", {}, p.placarMandante), h("i", {}, "x"), h("span", {}, p.placarVisitante)),
                h("div", { class: "placar-time" }, escudo(p.visitante.sigla, "medio"), h("strong", {}, p.visitante.nome))),
            h("div", { class: "placar-rodape" },
                h("time", { datetime: p.dataHora }, fmtDataHora(p.dataHora)),
                botoes(p, "icone")));
    },
};

/* ==========================================================================
   CHAVES DE API (área administrativa: tudo exige chave, até a leitura)
   ========================================================================== */

function mostrarChaveGerada(nova) {
    const copiar = h("button", { type: "button", class: "btn-icone", "aria-label": "Copiar chave", title: "Copiar", onClick: async () => {
        try {
            await navigator.clipboard.writeText(nova.chave);
            toast("ok", "Chave copiada.");
        } catch (e) {
            toast("aviso", "Não foi possível copiar automaticamente: selecione e copie à mão.");
        }
    } }, icone("copiar"));
    abrirDialogo({
        titulo: nova.nome, kicker: "Chave criada",
        conteudo: [
            h("div", { class: "alerta info" }, "Copie agora: por segurança a chave completa só aparece desta vez. No servidor fica apenas o hash dela."),
            h("div", { class: "chave-gerada" }, h("span", {}, nova.chave), copiar),
            h("p", { class: "cartao-meta" }, "Entregue a chave só a quem for usar a API e revogue-a quando não precisar mais."),
        ],
        rodape: [h("button", { type: "button", class: "botao primario", onClick: (ev) => ev.target.closest("dialog").close() }, "Entendi")],
    });
}

const chaves = {
    rota: "/api-keys", titulo: "Chaves de API", kicker: "Acesso", singular: "chave", layout: "tabela", exigeChave: true,
    semEdicao: true, rotuloExcluir: "Revogar", relacaoExcluir: "revoke", verboExcluido: "revogada", rotuloNovo: "Nova chave", artigoNovo: "uma",
    descricao: "Cada chave identifica quem usa a API. A chave completa só aparece ao criar; depois só o prefixo. Revogar a invalida na hora.",
    nomeItem: (k) => k.nome,
    ocultarExclusao: (k) => !k.ativa,
    textoExcluir: (k) => (k.prefixo && getChave().startsWith(k.prefixo)
        ? `"${k.nome}" é a chave que você está usando agora. Se revogá-la, será preciso digitar outra para continuar editando. Revogar mesmo assim?`
        : `Revogar a chave "${k.nome}"? Quem a usa passa a receber 401 imediatamente.`),
    colunas: [
        { titulo: "ID", classe: "id", render: (k) => k.id },
        { titulo: "Nome", render: (k) => h("strong", {}, k.nome) },
        { titulo: "Prefixo", classe: "mono", render: (k) => `${k.prefixo}…` },
        { titulo: "Situação", render: (k) => (k.ativa ? chip("Ativa", "ativa") : chip("Revogada", "revogada")) },
        { titulo: "Criada em", render: (k) => fmtDataHora(k.criadaEm) },
    ],
    criar: (ctx) => abrirFormulario({
        titulo: "Nova chave de API", kicker: "Acesso", rotuloSalvar: "Criar chave",
        campos: [{
            caminho: "nome", rotulo: "Nome", obrigatorio: true, maxlength: 100, inteira: true, placeholder: "ex.: frontend-github-pages",
            ajuda: "Identifica quem vai usar a chave.",
        }],
        aoSalvar: async (corpo) => {
            const { dados } = await chamar(`${API}/api-keys`, { metodo: "POST", corpo });
            mostrarChaveGerada(dados);
            await ctx.recarregar();
        },
    }),
};

export const RECURSOS = { times, estadios, jogadores, campeonatos, partidas, chaves };
