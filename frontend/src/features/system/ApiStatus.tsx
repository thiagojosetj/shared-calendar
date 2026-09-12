import type { ApiHealthStatus } from './api/getApiHealth'
import styles from './ApiStatus.module.css'
import { useApiHealth } from './useApiHealth'

const STATUS_MESSAGES: Record<ApiHealthStatus, string> = {
  UP: 'A API está disponível.',
  DOWN: 'A API respondeu, mas algum componente está fora do ar.',
  OUT_OF_SERVICE: 'A API está fora de serviço no momento.',
  UNKNOWN: 'A API respondeu com um estado desconhecido.',
}

/**
 * Mostra se o frontend consegue falar com o backend.
 *
 * Trata explicitamente os três estados que toda tela com dados precisa ter (RN-UX-07): carregando,
 * sucesso e erro. O elemento `<output>` tem o papel implícito `status`, então leitores de tela
 * anunciam a mudança sem roubar o foco. Ele só aceita conteúdo inline, por isso as mensagens são
 * `<span>` exibidos como bloco.
 */
export function ApiStatus() {
  const { data, isPending, isError, isFetching, refetch } = useApiHealth()

  return (
    <section className={styles.card} aria-labelledby="api-status-title">
      <h2 id="api-status-title" className={styles.title}>
        Conexão com a API
      </h2>

      <output className={styles.body}>
        {isPending && <span className={styles.muted}>Verificando a API…</span>}

        {isError && (
          <>
            <span className={styles.error}>Não foi possível conectar à API.</span>
            <span className={styles.muted}>
              Confira se o backend está rodando em <code>localhost:8080</code>.
            </span>
          </>
        )}

        {data && (
          <span className={data.status === 'UP' ? styles.success : styles.error}>
            {STATUS_MESSAGES[data.status]}
          </span>
        )}
      </output>

      {isError && (
        <button
          type="button"
          className={styles.retry}
          onClick={() => void refetch()}
          disabled={isFetching}
        >
          {isFetching ? 'Tentando…' : 'Tentar novamente'}
        </button>
      )}
    </section>
  )
}
