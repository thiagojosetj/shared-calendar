import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { routes } from './router'

const router = createBrowserRouter(routes)

export function App() {
  // Criado dentro do componente, e não no módulo, para que cada montagem da aplicação tenha seu
  // próprio cache.
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // Recarregar tudo a cada troca de aba geraria requisições sem necessidade.
            refetchOnWindowFocus: false,
          },
        },
      }),
  )

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}
