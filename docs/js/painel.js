// Tela inicial: contadores de cada recurso, últimas partidas e estado da conexão.

import { API, chamar, extrairLista, getChave, getSituacaoChave, getVersao } from "./api.js";
import { descreverErro } from "./erros.js";
import { RECURSOS } from "./recursos.js";
import { estadoVazio, fmtNumero, h, icone, limpar } from "./ui.js";

const CONTADORES = [
    ["times", "Times", "time"], ["estadios", "Estádios", "estadio"], ["jogadores", "Jogadores", "jogador"],
    ["campeonatos", "Campeonatos", "campeonato"], ["partidas", "Partidas", "partida"],
];

export function montarPainel(raiz) {
    const numeros = new Map(CONTADORES.map(([chave, rotulo]) => [chave, h("strong", {}, "…")]));
    const aviso = h("div", {});
    const partidas = h("div", {});

    raiz.append(
        h("header", { class: "vista-cab" },
            h("div", {}, h("p", { class: "kicker" }, "Visão geral"), h("h1", {}, "Painel"),
                h("p", { class: "vista-desc" }, "Resumo do campeonato e do estado da conexão com a API."))),
        aviso,
        h("div", { class: "contadores" }, CONTADORES.map(([chave, rotulo]) => h("a", { class: "contador", href: `#/${chave}` },
            h("span", {}, rotulo), numeros.get(chave)))),
        h("h2", { class: "subtitulo" }, "Últimas partidas"),
        partidas,
        conexao());

    // Um pedido por recurso, pedindo só 1 item: a resposta já traz o total em "page.totalElements"
    CONTADORES.forEach(async ([chave]) => {
        const tamanho = chave === "partidas" ? 4 : 1;
        const ordem = chave === "partidas" ? "dataHora,desc" : "id,desc";
        try {
            const { dados } = await chamar(`${API}${RECURSOS[chave].rota}?size=${tamanho}&sort=${ordem}`);
            numeros.get(chave).textContent = fmtNumero(dados.page ? dados.page.totalElements : extrairLista(dados).length);
            if (chave === "partidas") desenharPartidas(extrairLista(dados));
        } catch (e) {
            numeros.get(chave).textContent = "—";
            if (aviso.childElementCount === 0) {
                const d = descreverErro(e);
                aviso.append(estadoVazio({ erro: d.tipo === "erro", titulo: "Não foi possível carregar os dados", texto: d.texto }));
                aviso.style.marginBottom = "24px";
            }
            if (chave === "partidas") limpar(partidas);
        }
    });

    function desenharPartidas(itens) {
        limpar(partidas).append(itens.length
            ? h("div", { class: "grade-placares" }, itens.map((p) => RECURSOS.partidas.renderPlacar(p, () => null)))
            : estadoVazio({ titulo: "Sem partidas", texto: "Cadastre campeonatos, times e partidas para ver o placar aqui." }));
    }
}

function conexao() {
    const situacao = { sem: "Nenhuma chave informada (somente leitura)", verificando: "Verificando…", valida: "Chave válida", invalida: "Chave recusada pela API", erro: "Não foi possível verificar" };
    const v2 = getVersao() === "2";
    return h("section", { class: "conexao", "aria-label": "Estado da conexão" },
        h("h2", { class: "subtitulo" }, icone("chave", 20), "Conexão"),
        h("dl", {},
            h("dt", {}, "API"), h("dd", {}, API),
            h("dt", {}, "Versão"), h("dd", {}, v2 ? "V2: chave de API, rate limit, CORS e idempotência" : "V1: API original (sem proteções)"),
            h("dt", {}, "Chave"), h("dd", {}, v2 ? (getChave() ? situacao[getSituacaoChave()] : situacao.sem) : "Não usada na V1"),
            h("dt", {}, "Leitura"), h("dd", {}, "Pública (qualquer pessoa consulta)"),
            h("dt", {}, "Escrita"), h("dd", {}, v2 ? "Exige chave de API válida" : "Livre na V1")));
}
