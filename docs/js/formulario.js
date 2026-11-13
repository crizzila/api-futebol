// Formulário genérico em diálogo, montado a partir de uma lista de campos.
// Cada campo tem um "caminho" (ex.: "estadio.nome") que diz onde o valor entra no JSON enviado à API.

import { ErroApi } from "./api.js";
import { descreverErro } from "./erros.js";
import { abrirDialogo, definirCaminho, h, lerCaminho } from "./ui.js";

const TIPOS_INPUT = { texto: "text", numero: "number", data: "date", datahora: "datetime-local" };
let contador = 0;

/**
 * campos: [{ caminho, rotulo, tipo, obrigatorio, min, max, minlength, maxlength, pattern, placeholder, ajuda,
 *            maiusculas, inteira, opcoes, dependeDe, valorPadrao, valorNumerico }] ou { grupo: "Título" } (separador)
 * aoSalvar(corpo): função async; se lançar ErroApi, o erro aparece no próprio formulário.
 */
export function abrirFormulario({ titulo, kicker = "", campos, valores = {}, rotuloSalvar = "Salvar", aoSalvar, larga = false }) {
    const idForm = `form-${++contador}`;
    const form = h("form", { class: "form-grade", id: idForm, novalidate: true });
    const alerta = h("div", { hidden: true });
    const refs = new Map(); // caminho -> { def, entrada, caixa, erro }

    /* ----- monta os campos ----- */
    for (const def of campos) {
        if (def.grupo) {
            form.append(h("h3", { class: "grupo-titulo" }, def.grupo));
            continue;
        }
        const id = `${idForm}-${def.caminho.replace(/\./g, "-")}`;
        const erro = h("span", { class: "erro-campo", hidden: true });
        let entrada;
        if (def.tipo === "selecao") {
            entrada = h("select", { id, required: !!def.obrigatorio });
        } else {
            entrada = h("input", {
                id, type: TIPOS_INPUT[def.tipo || "texto"], required: !!def.obrigatorio, placeholder: def.placeholder,
                minlength: def.minlength, maxlength: def.maxlength, pattern: def.pattern, min: def.min, max: def.max,
                step: def.passo, autocomplete: "off",
            });
            if (def.maiusculas) entrada.addEventListener("input", () => { entrada.value = entrada.value.toUpperCase(); });
        }
        const caixa = h("div", { class: `campo ${def.inteira ? "inteira" : ""}`.trim() },
            h("label", { for: id }, def.rotulo), entrada,
            def.ajuda ? h("span", { class: "ajuda" }, def.ajuda) : null, erro);
        refs.set(def.caminho, { def, entrada, caixa, erro });
        form.append(caixa);
    }

    /* ----- valores iniciais e listas de seleção ----- */
    function inicial(def) {
        const v = lerCaminho(valores, def.caminho);
        if (v === undefined || v === null) return def.valorPadrao !== undefined ? String(def.valorPadrao) : "";
        if (def.tipo === "data") return String(v).slice(0, 10);
        if (def.tipo === "datahora") return String(v).slice(0, 16);
        return String(v);
    }

    function valoresAtuais() {
        const obj = {};
        refs.forEach((ref, caminho) => definirCaminho(obj, caminho, ref.entrada.value));
        return obj;
    }

    async function carregarOpcoes(ref, manter) {
        const { def, entrada } = ref;
        const alvo = manter !== undefined ? manter : inicial(def);
        entrada.replaceChildren(h("option", { value: "" }, "Carregando…"));
        entrada.disabled = true;
        try {
            const lista = typeof def.opcoes === "function" ? await def.opcoes(valoresAtuais()) : def.opcoes;
            entrada.replaceChildren(h("option", { value: "" }, lista.length ? "Selecione…" : (def.semOpcoes || "Nenhuma opção disponível")));
            lista.forEach((o) => entrada.append(h("option", { value: String(o.valor) }, o.rotulo)));
            entrada.value = lista.some((o) => String(o.valor) === alvo) ? alvo : "";
            entrada.disabled = false;
        } catch (e) {
            entrada.replaceChildren(h("option", { value: "" }, "Não foi possível carregar"));
            mostrarAlerta(descreverErro(e));
        }
    }

    // Um campo que depende de outro (ex.: times do campeonato) só carrega depois que o outro terminou de carregar,
    // senão, ao editar, ele leria o valor ainda vazio e ficaria sem opções.
    const carregamentos = new Map();
    function iniciarSelecao(ref) {
        if (!carregamentos.has(ref)) {
            const origem = ref.def.dependeDe && refs.get(ref.def.dependeDe);
            carregamentos.set(ref, origem && origem.def.tipo === "selecao"
                ? iniciarSelecao(origem).then(() => carregarOpcoes(ref))
                : carregarOpcoes(ref));
        }
        return carregamentos.get(ref);
    }
    refs.forEach((ref) => {
        if (ref.def.tipo === "selecao") {
            iniciarSelecao(ref);
        } else {
            ref.entrada.value = inicial(ref.def);
        }
    });
    // Campos que dependem de outro (ex.: times do campeonato escolhido) recarregam quando o outro muda
    refs.forEach((ref) => {
        if (ref.def.dependeDe && refs.has(ref.def.dependeDe)) {
            refs.get(ref.def.dependeDe).entrada.addEventListener("change", () => carregarOpcoes(ref, ""));
        }
    });

    /* ----- erros ----- */
    function limparErros() {
        alerta.hidden = true;
        alerta.replaceChildren();
        refs.forEach((ref) => { ref.caixa.classList.remove("com-erro"); ref.erro.hidden = true; ref.erro.textContent = ""; });
    }

    function mostrarAlerta({ tipo, texto, itens = [] }) {
        alerta.hidden = false;
        alerta.className = `alerta ${tipo === "erro" ? "erro" : "info"}`;
        alerta.replaceChildren(...[texto, itens.length ? h("ul", {}, itens.map((i) => h("li", {}, i))) : null].filter(Boolean));
    }

    function tratarErro(e) {
        if (e instanceof ErroApi && e.status === 400 && Object.keys(e.campos).length) {
            const sobras = [];
            for (const [caminho, msg] of Object.entries(e.campos)) {
                const ref = refs.get(caminho);
                if (ref) {
                    ref.caixa.classList.add("com-erro");
                    ref.erro.hidden = false;
                    ref.erro.textContent = msg;
                } else {
                    sobras.push(`${caminho}: ${msg}`);
                }
            }
            if (sobras.length) mostrarAlerta({ tipo: "erro", texto: "Dados inválidos:", itens: sobras });
            else mostrarAlerta({ tipo: "erro", texto: "Alguns campos precisam de correção (destacados abaixo)." });
            return;
        }
        mostrarAlerta(descreverErro(e));
    }

    /* ----- monta o JSON ----- */
    function montarCorpo() {
        const corpo = {};
        refs.forEach(({ def, entrada }, caminho) => {
            let v = entrada.value.trim();
            if (v === "") return; // campo opcional vazio não é enviado
            if (def.tipo === "numero" || def.valorNumerico) v = Number(v);
            else if (def.tipo === "datahora" && v.length === 16) v += ":00"; // a API espera segundos
            else if (def.maiusculas) v = v.toUpperCase();
            definirCaminho(corpo, caminho, v);
        });
        return corpo;
    }

    /* ----- envio ----- */
    const botaoSalvar = h("button", { type: "submit", class: "botao primario", form: idForm }, rotuloSalvar);
    const dialogo = abrirDialogo({
        titulo, kicker, larga,
        conteudo: [alerta, form],
        rodape: [h("button", { type: "button", class: "botao", onClick: () => dialogo.fechar() }, "Cancelar"), botaoSalvar],
    });

    form.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        limparErros();
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        botaoSalvar.disabled = true;
        botaoSalvar.textContent = "Salvando…";
        try {
            await aoSalvar(montarCorpo());
            dialogo.fechar();
        } catch (e) {
            tratarErro(e);
        } finally {
            botaoSalvar.disabled = false;
            botaoSalvar.textContent = rotuloSalvar;
        }
    });

    const primeiro = form.querySelector("input, select");
    if (primeiro) primeiro.focus();
    return dialogo;
}
