import type { ReactNode } from 'react'
import { CalendarIcon, GroupsIcon, HomeIcon, NotesIcon } from '../../shared/ui/icons'

export interface NavItem {
  to: string
  label: string
  icon: ReactNode
}

/**
 * Menu principal definido em RN-UX-01.
 *
 * Fica fora do arquivo do layout porque um módulo que exporta componentes e também constantes quebra o
 * fast refresh do Vite.
 *
 * "Início" aponta para "/", mas não fica ativo em "/calendario": o NavLink do React Router só considera
 * uma rota filha quando o caractere seguinte ao caminho do link é "/", e para o link raiz esse caractere
 * nunca é. Por isso não é preciso `end` aqui.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', label: 'Início', icon: <HomeIcon /> },
  { to: '/calendario', label: 'Calendário', icon: <CalendarIcon /> },
  { to: '/grupos', label: 'Grupos', icon: <GroupsIcon /> },
  { to: '/notas', label: 'Notas', icon: <NotesIcon /> },
]
