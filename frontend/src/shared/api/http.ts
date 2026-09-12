import axios from 'axios'

/**
 * Cliente HTTP único da aplicação.
 *
 * A autenticação é por cookie de sessão HttpOnly, e não por token guardado no navegador (ADR-0003).
 * Por isso nenhum token é lido nem gravado aqui.
 *
 * - `withCredentials`: envia o cookie de sessão nas requisições.
 * - `withXSRFToken`: envia o header CSRF também em requisições cross-origin. Desde o axios 1.6.2,
 *   `withCredentials` sozinho não basta; sem esta opção, todo POST responderia 403 no dia em que o
 *   frontend rodasse em outra origem. Com o proxy do Vite o problema ficaria escondido.
 * - `xsrfCookieName` / `xsrfHeaderName`: os nomes que o Spring Security usa com `csrf.spa()`.
 */
export const http = axios.create({
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 10_000,
})
