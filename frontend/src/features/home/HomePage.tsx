import { EmptyState } from '../../shared/ui/EmptyState'
import { PageHeader } from '../../shared/ui/PageHeader'
import { ApiStatus } from '../system/ApiStatus'
import styles from './HomePage.module.css'

export function HomePage() {
  return (
    <>
      <PageHeader
        title="Início"
        description="Seu painel com os compromissos de hoje, os próximos eventos e os convites pendentes."
      />
      <div className={styles.grid}>
        <ApiStatus />
        <EmptyState title="Nenhum compromisso por enquanto">
          Os compromissos e convites aparecem aqui quando a criação de eventos estiver disponível.
        </EmptyState>
      </div>
    </>
  )
}
