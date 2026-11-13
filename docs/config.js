// Endereço da API. Em localhost (teste no seu computador) usa a API local;
// publicado no GitHub Pages usa o serviço do Render.
// ATENÇÃO: aqui NUNCA vai chave de API. A chave é digitada pelo usuário na página
// e fica só no sessionStorage do navegador (some ao fechar a aba).
const API_BASE_URL = ["localhost", "127.0.0.1"].includes(location.hostname)
    ? "http://localhost:8080"
    : "https://api-futebol-lfxw.onrender.com";
