// Traduz os erros da API (400, 401, 409, 422, 429...) em mensagens amigáveis.

import { ErroApi, getChave, getVersao } from "./api.js";
import { toast } from "./ui.js";

/** Devolve { tipo, texto, itens } pronto para mostrar. `rotulos` troca "estadio.nome" por "Nome do estádio". */
export function descreverErro(e, rotulos = {}) {
    if (!(e instanceof ErroApi)) {
        console.error(e);
        return { tipo: "erro", texto: "Erro inesperado no navegador. Veja o console para detalhes.", itens: [] };
    }
    if (e.rede) {
        if (getVersao() === "1") {
            return {
                tipo: "info",
                texto: "A versão 1 não envia cabeçalhos CORS, então o navegador bloqueou a resposta. É o esperado: a v1 é a API original, sem CORS. Volte para a V2.",
                itens: [],
            };
        }
        return {
            tipo: "erro",
            texto: "Não foi possível falar com a API. Confira se o serviço está no ar (no plano gratuito pode levar cerca de 1 minuto para acordar) e se o endereço desta página está em CORS_ALLOWED_ORIGINS.",
            itens: [],
        };
    }

    const mensagem = e.mensagem;
    switch (e.status) {
        case 400: {
            const campos = Object.entries(e.campos);
            if (campos.length) {
                return {
                    tipo: "erro", texto: "Dados inválidos. Corrija e tente de novo:",
                    itens: campos.map(([campo, msg]) => `${rotulos[campo] || campo}: ${msg}`),
                };
            }
            return { tipo: "erro", texto: mensagem || "A API não aceitou os dados enviados.", itens: [] };
        }
        case 401:
            return {
                tipo: "erro", itens: [],
                texto: getChave()
                    ? "Chave de API inválida ou revogada. Confira a chave digitada no topo da página."
                    : "Esta ação exige uma chave de API. Digite a sua no campo \"Chave de API\" (versão 2).",
            };
        case 404:
            return { tipo: "erro", texto: mensagem || "Registro não encontrado (talvez já tenha sido removido).", itens: [] };
        case 409:
            return { tipo: "erro", texto: mensagem || "Conflito: a operação não é permitida agora.", itens: [] };
        case 422:
            return { tipo: "erro", texto: `Chave de idempotência reutilizada com outro conteúdo. ${mensagem}`, itens: [] };
        case 429:
            return {
                tipo: "aviso", itens: [],
                texto: `Muitas requisições em pouco tempo. Aguarde ${e.retryAfter || "alguns"} segundos e tente de novo.`,
            };
        default:
            return { tipo: "erro", texto: `Erro ${e.status}: ${mensagem || "a API não conseguiu atender o pedido."}`, itens: [] };
    }
}

export function mostrarErro(e, rotulos = {}) {
    const d = descreverErro(e, rotulos);
    toast(d.tipo, d.texto, { itens: d.itens, duracao: 7000 });
}
