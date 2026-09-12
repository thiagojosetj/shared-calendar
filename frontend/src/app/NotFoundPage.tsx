import { Link } from 'react-router'
import { EmptyState } from '../shared/ui/EmptyState'
import { PageHeader } from '../shared/ui/PageHeader'

export function NotFoundPage() {
  return (
    <>
      <PageHeader title="Página não encontrada" />
      <EmptyState title="Este endereço não existe">
        Confira o link ou volte para o início pelo menu.
      </EmptyState>
      <p>
        <Link to="/">Voltar para o início</Link>
      </p>
    </>
  )
}
