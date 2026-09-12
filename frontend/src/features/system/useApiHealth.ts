import { useQuery } from '@tanstack/react-query'
import { getApiHealth } from './api/getApiHealth'

export const apiHealthQueryKey = ['system', 'api-health'] as const

/**
 * Estado de saúde da API, com cache, nova tentativa e estados de carregamento e erro prontos.
 *
 * Uma única nova tentativa: se a API está fora, a pessoa deve ver o erro em segundos, e não depois das
 * três tentativas com espera crescente do padrão do TanStack Query.
 */
export function useApiHealth() {
  return useQuery({
    queryKey: apiHealthQueryKey,
    queryFn: getApiHealth,
    retry: 1,
    staleTime: 30_000,
  })
}
