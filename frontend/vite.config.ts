/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Em desenvolvimento, o Vite (porta 5173) repassa ao backend (porta 8080) as chamadas de API e de
// health check. Para o navegador tudo acontece na mesma origem, então o cookie de sessão e o token
// CSRF funcionam sem CORS (ADR-0003). Em produção o próprio Spring serve o build, com o mesmo efeito.
const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': BACKEND_URL,
      '/actuator': BACKEND_URL,
    },
  },
  test: {
    // jsdom simula o DOM do navegador para os testes de componente.
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Um teste não pode herdar mocks deixados por outro.
    restoreMocks: true,
  },
})
