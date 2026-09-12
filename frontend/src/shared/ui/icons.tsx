import type { SVGProps } from 'react'

/**
 * Ícones de traço simples, desenhados para o projeto.
 *
 * São sempre decorativos (`aria-hidden`): o nome acessível vem do texto ou do `aria-label` do elemento
 * que contém o ícone, nunca do desenho.
 */
function Icon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...props}
    />
  )
}

export function HomeIcon() {
  return (
    <Icon>
      <path d="M4 11.5 12 5l8 6.5" />
      <path d="M6.5 10v9h11v-9" />
    </Icon>
  )
}

export function CalendarIcon() {
  return (
    <Icon>
      <rect x="4" y="5.5" width="16" height="14" rx="2.5" />
      <path d="M4 10h16M9 3.5v4M15 3.5v4" />
    </Icon>
  )
}

export function GroupsIcon() {
  return (
    <Icon>
      <circle cx="9" cy="9" r="3" />
      <circle cx="16.5" cy="10" r="2.5" />
      <path d="M3.5 19c.8-3 3-4.5 5.5-4.5s4.7 1.5 5.5 4.5M15 15c2.3 0 4.2 1.2 5 4" />
    </Icon>
  )
}

export function NotesIcon() {
  return (
    <Icon>
      <path d="M6 4h9l3 3v13H6z" />
      <path d="M9 11h6M9 15h4" />
    </Icon>
  )
}

export function SearchIcon() {
  return (
    <Icon>
      <circle cx="11" cy="11" r="6" />
      <path d="m20 20-4.5-4.5" />
    </Icon>
  )
}

export function PlusIcon() {
  return (
    <Icon>
      <path d="M12 5v14M5 12h14" />
    </Icon>
  )
}

export function BellIcon() {
  return (
    <Icon>
      <path d="M6.5 16.5V11a5.5 5.5 0 0 1 11 0v5.5l1.5 1.5H5z" />
      <path d="M10 20.5a2.2 2.2 0 0 0 4 0" />
    </Icon>
  )
}

export function UserIcon() {
  return (
    <Icon>
      <circle cx="12" cy="8.5" r="3.5" />
      <path d="M5 20c1-3.6 3.8-5.5 7-5.5s6 1.9 7 5.5" />
    </Icon>
  )
}
