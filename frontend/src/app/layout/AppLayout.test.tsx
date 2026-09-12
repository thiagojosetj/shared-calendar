import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getApiHealth } from '../../features/system/api/getApiHealth'
import { renderRoute } from '../../test/renderRoute'

// A página inicial consulta a API. Nos testes de layout essa consulta é substituída, para que eles não
// dependam de rede.
vi.mock('../../features/system/api/getApiHealth', () => ({ getApiHealth: vi.fn() }))

describe('AppLayout', () => {
  beforeEach(() => {
    vi.mocked(getApiHealth).mockResolvedValue({ status: 'UP' })
  })

  it('mostra o menu principal com as quatro seções (RN-UX-01)', () => {
    renderRoute('/calendario')

    const menu = screen.getByRole('navigation', { name: 'Menu principal' })
    const links = within(menu).getAllByRole('link')

    expect(links.map((link) => link.textContent)).toEqual([
      'Início',
      'Calendário',
      'Grupos',
      'Notas',
    ])
  })

  it('marca como atual apenas a seção aberta', () => {
    renderRoute('/calendario')

    const menu = screen.getByRole('navigation', { name: 'Menu principal' })
    expect(within(menu).getByRole('link', { name: 'Calendário' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    // "Início" aponta para "/", que é prefixo de todas as rotas, e mesmo assim não pode aparecer ativo.
    expect(within(menu).getByRole('link', { name: 'Início' })).not.toHaveAttribute('aria-current')
  })

  it('navega entre as seções pelo menu', async () => {
    const user = userEvent.setup()
    renderRoute('/')

    expect(await screen.findByRole('heading', { level: 1, name: 'Início' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Grupos' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Grupos' })).toBeInTheDocument()
  })

  it('oferece um atalho de teclado para pular direto ao conteúdo (RN-UX-09)', () => {
    renderRoute('/notas')

    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveAttribute(
      'href',
      '#conteudo',
    )
    expect(screen.getByRole('main')).toHaveAttribute('id', 'conteudo')
  })

  it('mantém desabilitados os controles globais que ainda não funcionam', () => {
    renderRoute('/notas')

    expect(screen.getByRole('searchbox', { name: 'Pesquisar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Criar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Notificações' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Perfil' })).toBeDisabled()
  })

  it('mostra uma página própria para endereços inexistentes', () => {
    renderRoute('/endereco-que-nao-existe')

    expect(
      screen.getByRole('heading', { level: 1, name: 'Página não encontrada' }),
    ).toBeInTheDocument()
    // O layout continua presente, então a pessoa consegue voltar pelo menu.
    expect(screen.getByRole('navigation', { name: 'Menu principal' })).toBeInTheDocument()
  })
})
