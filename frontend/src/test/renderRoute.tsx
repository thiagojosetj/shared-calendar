import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { routes } from '../app/router'

/**
 * Renderiza a aplicação real (mesmas rotas e layout) em uma URL específica.
 *
 * Usa um roteador em memória, para não depender da URL do navegador, e um QueryClient novo por teste,
 * sem novas tentativas, para que um teste não herde cache nem espere retries de outro.
 */
export function renderRoute(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const router = createMemoryRouter(routes, { initialEntries: [path] })

  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}
