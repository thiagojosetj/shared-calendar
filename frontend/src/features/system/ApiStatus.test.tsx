import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getApiHealth } from './api/getApiHealth'
import { ApiStatus } from './ApiStatus'

vi.mock('./api/getApiHealth', () => ({ getApiHealth: vi.fn() }))
const getApiHealthMock = vi.mocked(getApiHealth)

function renderApiStatus() {
  // retryDelay 0: a consulta real faz uma nova tentativa, e o teste não deve esperar o intervalo dela.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ApiStatus />
    </QueryClientProvider>,
  )
}

describe('ApiStatus', () => {
  beforeEach(() => {
    getApiHealthMock.mockReset()
  })

  it('mostra que está verificando e depois que a API está disponível', async () => {
    getApiHealthMock.mockResolvedValue({ status: 'UP' })

    renderApiStatus()

    expect(screen.getByRole('status')).toHaveTextContent('Verificando a API…')
    expect(await screen.findByText('A API está disponível.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tentar novamente' })).not.toBeInTheDocument()
  })

  it('avisa quando a API responde, mas com um componente fora do ar', async () => {
    getApiHealthMock.mockResolvedValue({ status: 'DOWN' })

    renderApiStatus()

    expect(
      await screen.findByText('A API respondeu, mas algum componente está fora do ar.'),
    ).toBeInTheDocument()
  })

  it('mostra o erro de conexão e permite tentar novamente', async () => {
    const user = userEvent.setup()
    getApiHealthMock.mockRejectedValue(new Error('Network Error'))

    renderApiStatus()

    expect(await screen.findByText('Não foi possível conectar à API.')).toBeInTheDocument()

    getApiHealthMock.mockResolvedValue({ status: 'UP' })
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('A API está disponível.')).toBeInTheDocument()
    expect(screen.queryByText('Não foi possível conectar à API.')).not.toBeInTheDocument()
  })
})
