import { http } from '../../../shared/api/http'

/** Estados que o Spring Boot Actuator pode informar. */
export type ApiHealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

export interface ApiHealth {
  status: ApiHealthStatus
}

const KNOWN_STATUSES: readonly ApiHealthStatus[] = ['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN']

/**
 * Consulta o health check do backend.
 *
 * O Actuator responde 503 quando algum componente, como o banco, está fora. Esse 503 é uma resposta
 * válida que diz "a API está no ar, mas com problema", e não uma falha de rede. Por isso ele é aceito
 * e o status é lido do corpo. Qualquer outro status, ou a ausência de resposta, vira erro.
 */
export async function getApiHealth(): Promise<ApiHealth> {
  const response = await http.get<unknown>('/actuator/health', {
    validateStatus: (status) => status === 200 || status === 503,
  })
  return { status: parseStatus(response.data) }
}

function parseStatus(body: unknown): ApiHealthStatus {
  if (typeof body === 'object' && body !== null && 'status' in body) {
    const { status } = body
    if (typeof status === 'string' && isKnownStatus(status)) {
      return status
    }
  }
  return 'UNKNOWN'
}

function isKnownStatus(value: string): value is ApiHealthStatus {
  return (KNOWN_STATUSES as readonly string[]).includes(value)
}
