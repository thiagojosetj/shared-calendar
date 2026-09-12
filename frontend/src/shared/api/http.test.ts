import { describe, expect, it } from 'vitest'
import { http } from './http'

describe('cliente HTTP', () => {
  it('envia o cookie de sessão nas requisições', () => {
    expect(http.defaults.withCredentials).toBe(true)
  })

  it('envia o header CSRF também em requisições cross-origin', () => {
    // Sem esta opção, desde o axios 1.6.2 o header não é enviado cross-origin e todo POST responde 403
    // (ADR-0003). Com o proxy do Vite o problema ficaria escondido, por isso a garantia é um teste.
    expect(http.defaults.withXSRFToken).toBe(true)
  })

  it('usa os nomes de cookie e header do csrf.spa() do Spring Security', () => {
    expect(http.defaults.xsrfCookieName).toBe('XSRF-TOKEN')
    expect(http.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN')
  })
})
