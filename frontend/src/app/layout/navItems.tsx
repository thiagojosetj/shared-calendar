import type { ReactNode } from 'react'
import { CalendarIcon, GroupsIcon, HomeIcon, NotesIcon } from '../../shared/ui/icons'

export interface NavItem {
  to: string
  label: string
  icon: ReactNode
  /** Só marca "Início" como ativo na rota exata, e não em todas as rotas filhas de "/". */
  end?: boolean
}

/**
 * Menu principal definido em RN-UX-01.
 *
 * Fica fora do arquivo do layout porque um módulo que exporta componentes e também constantes quebra o
 * fast refresh do Vite.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Início', icon: <HomeIcon />, end: true },
  { to: '/calendario', label: 'Calendário', icon: <CalendarIcon /> },
  { to: '/grupos', label: 'Grupos', icon: <GroupsIcon /> },
  { to: '/notas', label: 'Notas', icon: <NotesIcon /> },
]
