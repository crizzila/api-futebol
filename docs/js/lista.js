// Motor das telas de cadastro: lista paginada (seguindo os links HATEOAS), busca, criar, editar e excluir.
// Cada tela só descreve o que é diferente (campos, colunas, cartões) em recursos.js.

import { API, chamar, extrairLista, linkDe, novaChaveIdempotencia, podeEscrever, registro, repetir } from "./api.js";
import { mostrarErro, descreverErro } from "./erros.js";
import { abrirFormulario } from "./formulario.js";
import { confirmar, estadoVazio, h, icone, limpar, toast } from "./ui.js";

const TAMANHO_PAGINA = 9;

export function montarLista(rec, raiz) {
    const estado = { url: null, links: {}, busca: null, geracao: 0 };
    const resultado = h("div", { class: "resultado" });
    const paginacao = h("nav", { class: "paginacao", "aria-label": "Paginação" });
    const rotulosDosCampos = Object.fromEntries((rec.campos || []).filter((c) => c.caminho).map((c) => [c.caminho, c.rotulo]));

    /* ---------- cabeçalho ---------- */
    const botaoNovo = rec.somenteLeitura ? null
        : h("button", { type: "button", class: "botao primario acao-escrita", onClick: () => executar({ escrita: true, fn: novo }) },
            icone("mais"), rec.rotuloNovo || `Novo ${rec.singular}`);

    raiz.append(
        h("header", { class: "vista-cab" },
            h("div", {}, h("p", { class: "kicker" }, rec.kicker), h("h1", {}, rec.titulo),
                rec.descricao ? h("p", { class: "vista-desc" }, rec.descricao) : null),
            botaoNovo),
        montarBusca(),
        resultado,
        paginacao);

    /* ---------- busca ---------- */
    function montarBusca() {
        if (!rec.buscas || !rec.buscas.length) return null;
        const tipo = h("select", { id: "busca-tipo", "aria-label": "Buscar por" }, rec.buscas.map((b, i) => h("option", { value: i }, b.rotulo)));
        const valorBox = h("span", { style: "display:contents" });
        const form = h("form", { class: "busca", role: "search" }, h("label", { for: "busca-tipo" }, "Buscar por"));

        function campoValor() {
            const b = rec.buscas[Number(tipo.value)];
            limpar(valorBox);
            if (b.tipo === "selecao") {
                valorBox.append(h("select", { id: "busca-valor", "aria-label": b.rotulo }, b.opcoes.map((o) => h("option", { value: o.valor }, o.rotulo))));
            } else {
                valorBox.append(h("input", {
                    id: "busca-valor", type: b.tipo === "numero" ? "number" : "text", placeholder: b.placeholder || b.rotulo,
                    "aria-label": b.rotulo, required: true,
                }));
            }
        }
        tipo.addEventListener("change", campoValor);
        campoValor();

        form.append(tipo, valorBox,
            h("button", { type: "submit", class: "botao" }, icone("busca"), "Buscar"),
            h("button", { type: "button", class: "btn-texto", onClick: () => { estado.busca = null; campoValor(); carregar(urlInicial()); } }, "Limpar busca"));
        form.addEventListener("submit", (ev) => {
            ev.preventDefault();
            const b = rec.buscas[Number(tipo.value)];
            estado.busca = { b, valor: form.querySelector("#busca-valor").value.trim() };
            carregar(urlInicial());
        });
        return form;
    }

    function urlInicial() {
        const ordem = `page=0&size=${TAMANHO_PAGINA}&sort=${rec.ordem || "id,desc"}`;
        if (estado.busca) {
            const { b, valor } = estado.busca;
            return `${API}${rec.rota}${b.caminho}?${b.param}=${encodeURIComponent(valor)}&${ordem}`;
        }
        return `${API}${rec.rota}?${ordem}`;
    }

    /* ---------- carregar e desenhar ---------- */
    async function carregar(url) {
        const minha = ++estado.geracao; // se o usuário clicar de novo antes de chegar, só a última resposta vale
        estado.url = url;
        limpar(resultado).append(...Array.from({ length: 3 }, () => h("div", { class: "esqueleto", style: "margin-bottom:14px" })));
        limpar(paginacao);
        try {
            const { dados } = await chamar(url, { comChave: !!rec.exigeChave });
            if (minha !== estado.geracao) return;
            desenhar(dados);
        } catch (e) {
            if (minha !== estado.geracao) return;
            const d = descreverErro(e);
            limpar(resultado).append(estadoVazio({
                erro: d.tipo === "erro", titulo: e.status === 401 ? "Acesso restrito" : "Não foi possível carregar",
                texto: d.texto,
                acao: h("button", { type: "button", class: "botao", onClick: () => carregar(url) }, icone("repetir"), "Tentar de novo"),
            }));
        }
    }

    function desenhar(dados) {
        const itens = extrairLista(dados);
        const pagina = dados.page || {};
        estado.links = dados._links || {};
        limpar(resultado);

        if (!itens.length) {
            resultado.append(estadoVazio({
                titulo: estado.busca ? "Nada encontrado" : `Nenhum registro de ${rec.titulo.toLowerCase()}`,
                texto: estado.busca ? "Tente outro termo ou limpe a busca." : `Quando você cadastrar ${rec.artigoNovo || "um novo"} ${rec.singular}, ele aparece aqui.`,
            }));
        } else if (rec.layout === "tabela") {
            resultado.append(tabela(itens));
        } else {
            resultado.append(h("div", { class: rec.layout === "placares" ? "grade-placares" : "grade-cartoes" },
                itens.map((item) => (rec.layout === "placares" ? rec.renderPlacar(item, botoes) : rec.renderCartao(item, botoes)))));
        }

        // Paginação: só segue os links que a própria API informa (HATEOAS)
        const total = pagina.totalElements ?? itens.length;
        // A API sempre informa "first" e "last": na primeira/última página eles não levam a lugar nenhum
        const semDestino = (relacao) => !estado.links[relacao]
            || (relacao === "first" && !estado.links.prev) || (relacao === "last" && !estado.links.next);
        const botao = (relacao, nome, rotulo) => h("button", {
            type: "button", "aria-label": rotulo, title: rotulo, disabled: semDestino(relacao),
            onClick: () => carregar(estado.links[relacao].href),
        }, icone(nome));
        paginacao.append(
            h("span", { class: "info" }, pagina.totalPages
                ? `Página ${pagina.number + 1} de ${pagina.totalPages} · ${total} registro${total === 1 ? "" : "s"}`
                : "Sem registros"),
            h("div", { class: "botoes" },
                botao("first", "dupla-esq", "Primeira página"), botao("prev", "seta-esq", "Página anterior"),
                botao("next", "seta-dir", "Próxima página"), botao("last", "dupla-dir", "Última página")));
    }

    function tabela(itens) {
        return h("div", { class: "tabela-wrap" }, h("table", {},
            h("thead", {}, h("tr", {},
                rec.colunas.map((c) => h("th", { class: c.classe || "" }, c.titulo)),
                h("th", { class: "num" }, h("span", { class: "sr" }, "Ações"))),
            ),
            h("tbody", {}, itens.map((item) => h("tr", {},
                rec.colunas.map((c) => h("td", { class: c.classe || "" }, c.render(item))),
                h("td", { class: "acoes" }, botoes(item, "icone")))))));
    }

    /* ---------- ações ---------- */
    // Sem chave válida (v2), as ações de escrita explicam o motivo em vez de falhar com 401
    async function executar(acao, item) {
        if (acao.escrita && !podeEscrever()) {
            toast("aviso", "Somente leitura: informe uma chave de API válida no topo da página para alterar dados (versão 2).");
            document.getElementById("chave").focus();
            return;
        }
        try {
            await acao.fn(item);
        } catch (e) {
            mostrarErro(e, rotulosDosCampos);
        }
    }

    function acoesDe(item) {
        const padrao = [];
        if (!rec.semEdicao) padrao.push({ rotulo: "Editar", icone: "editar", escrita: true, fn: editar });
        if (!rec.semExclusao && !(rec.ocultarExclusao && rec.ocultarExclusao(item))) {
            padrao.push({ rotulo: rec.rotuloExcluir || "Excluir", icone: "lixeira", escrita: true, perigo: true, fn: excluir });
        }
        const extras = rec.acoes ? rec.acoes(item, ctx) : [];
        return [...extras, ...padrao];
    }

    // variante "mini": botão com texto (cartões). "icone": só o ícone (tabelas e placares)
    function botoes(item, variante) {
        const lista = acoesDe(item).map((a) => h("button", {
            type: "button",
            class: variante === "mini"
                ? `btn-mini ${a.perigo ? "perigo" : ""} ${a.escrita ? "acao-escrita" : ""}`.trim()
                : `btn-icone ${a.perigo ? "perigo" : ""} ${a.escrita ? "acao-escrita" : ""}`.trim(),
            title: a.rotulo, "aria-label": `${a.rotulo}: ${rec.nomeItem(item)}`,
            onClick: () => executar(a, item),
        }, icone(a.icone), variante === "mini" ? a.rotulo : null));
        if (variante === "mini") return h("div", { class: "cartao-acoes" }, lista);
        return h("div", { class: "acoes-linha", style: "display:inline-flex;gap:2px" }, lista);
    }

    async function novo() {
        if (rec.criar) return rec.criar(ctx);
        abrirFormulario({
            titulo: `Novo ${rec.singular}`, kicker: rec.titulo, campos: rec.campos, rotuloSalvar: "Cadastrar", larga: rec.formLargo,
            aoSalvar: async (corpo) => {
                // Uma chave NOVA por cadastro: ela identifica esta operação (idempotência, só na v2)
                const { dados, replay } = await chamar(`${API}${rec.rota}`, {
                    metodo: "POST", corpo, idempotencyKey: novaChaveIdempotencia(),
                });
                avisarCriado(dados, replay);
                if (rec.aoMudar) rec.aoMudar();
                await carregar(urlInicial());
            },
        });
    }

    function avisarCriado(item, replay) {
        const nome = rec.nomeItem(item);
        if (replay) toast("info", `Requisição repetida: a API devolveu a resposta guardada (${nome}). Nada foi criado de novo.`);
        else toast("ok", `Cadastro realizado: ${nome}.`,
            { acao: { rotulo: "Repetir com a mesma chave", fn: repetirUltima } });
    }

    // Demonstração de idempotência: reenvia a última criação com a MESMA chave
    async function repetirUltima() {
        const ultima = registro.find((r) => r.metodo === "POST" && r.idempotencyKey && r.status >= 200 && r.status < 300);
        if (!ultima) return;
        try {
            const { dados, replay } = await repetir(ultima);
            toast(replay ? "info" : "ok", replay
                ? `Repetida com a mesma chave: a API respondeu "Idempotent-Replayed" e nada foi criado de novo (${rec.nomeItem(dados)}).`
                : "A API processou a requisição de novo.");
        } catch (e) {
            mostrarErro(e);
        }
    }

    function editar(item) {
        abrirFormulario({
            titulo: `Editar ${rec.singular}`, kicker: rec.nomeItem(item), campos: rec.campos, valores: item, larga: rec.formLargo,
            aoSalvar: async (corpo) => {
                const { dados } = await chamar(linkDe(item, "update"), { metodo: "PUT", corpo });
                toast("ok", `${rec.nomeItem(dados)} atualizado.`);
                if (rec.aoMudar) rec.aoMudar();
                await carregar(estado.url);
            },
        });
    }

    async function excluir(item) {
        const ok = await confirmar({
            titulo: `${rec.rotuloExcluir || "Excluir"} ${rec.singular}`, perigo: true, rotulo: rec.rotuloExcluir || "Excluir",
            texto: rec.textoExcluir ? rec.textoExcluir(item) : `Tem certeza que deseja excluir ${rec.nomeItem(item)}? Essa ação não pode ser desfeita.`,
        });
        if (!ok) return;
        await chamar(linkDe(item, rec.relacaoExcluir || "delete"), { metodo: "DELETE" });
        toast("ok", `${rec.nomeItem(item)}: ${rec.verboExcluido || "excluído"}.`);
        if (rec.aoMudar) rec.aoMudar();
        // Se a página ficou vazia, volta para a anterior
        const { dados } = await chamar(estado.url, { comChave: !!rec.exigeChave });
        if (!extrairLista(dados).length && estado.links.prev) await carregar(estado.links.prev.href);
        else desenhar(dados);
    }

    const ctx = {
        rec, recarregar: () => carregar(estado.url), executar,
    };

    carregar(urlInicial());
    return ctx;
}
