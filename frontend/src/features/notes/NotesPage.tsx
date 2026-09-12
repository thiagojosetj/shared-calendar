import { EmptyState } from '../../shared/ui/EmptyState'
import { PageHeader } from '../../shared/ui/PageHeader'

export function NotesPage() {
  return (
    <>
      <PageHeader
        title="Notas"
        description="Anotações dos seus eventos e dos seus grupos, privadas ou compartilhadas."
      />
      <EmptyState title="Nenhuma nota por enquanto">
        As notas de evento e de grupo chegam na Fase 5.
      </EmptyState>
    </>
  )
}
