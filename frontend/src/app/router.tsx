import type { RouteObject } from 'react-router'
import { CalendarPage } from '../features/calendar/CalendarPage'
import { GroupsPage } from '../features/groups/GroupsPage'
import { HomePage } from '../features/home/HomePage'
import { NotesPage } from '../features/notes/NotesPage'
import { AppLayout } from './layout/AppLayout'
import { NotFoundPage } from './NotFoundPage'

/**
 * Rotas da aplicação. Ficam separadas do roteador para que os testes montem as mesmas rotas em um
 * roteador em memória, sem depender da URL do navegador.
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'calendario', element: <CalendarPage /> },
      { path: 'grupos', element: <GroupsPage /> },
      { path: 'notas', element: <NotesPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
