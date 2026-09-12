import { EmptyState } from '../../shared/ui/EmptyState'
import { PageHeader } from '../../shared/ui/PageHeader'

export function GroupsPage() {
  return (
    <>
      <PageHeader
        title="Grupos"
        description="Os grupos dos quais você participa, com seus membros, papéis e calendários."
      />
      <EmptyState title="Você ainda não participa de nenhum grupo">
        A criação de grupos, os convites e o gerenciamento de membros chegam na Fase 2.
      </EmptyState>
    </>
  )
}
