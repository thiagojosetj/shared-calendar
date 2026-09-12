import styles from './EmptyState.module.css'

interface EmptyStateProps {
  title: string
  children: string
}

/** Estado vazio explícito, para que uma tela sem conteúdo nunca pareça quebrada (RN-UX-07). */
export function EmptyState({ title, children }: EmptyStateProps) {
  return (
    <div className={styles.empty}>
      <p className={styles.title}>{title}</p>
      <p className={styles.text}>{children}</p>
    </div>
  )
}
