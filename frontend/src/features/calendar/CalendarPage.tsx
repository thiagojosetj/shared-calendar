import { EmptyState } from '../../shared/ui/EmptyState'
import { PageHeader } from '../../shared/ui/PageHeader'

export function CalendarPage() {
  return (
    <>
      <PageHeader
        title="Calendário"
        description="Seu calendário pessoal e os calendários dos seus grupos, reunidos em uma única visão."
      />
      <EmptyState title="O calendário ainda não está disponível">
        As visualizações de mês, semana, dia e agenda chegam na Fase 3.
      </EmptyState>
    </>
  )
}
