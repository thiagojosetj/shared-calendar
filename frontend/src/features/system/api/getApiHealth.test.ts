import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { http } from '../../../shared/api/http'
import { getApiHealth } from './getApiHealth'

function respostaCom(data: unknown, status = 200) {
  return { data, status } as AxiosResponse<unknown>
}

describe('getApiHealth', () => {
  it('lê o status informado pelo Actuator', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(respostaCom({ status: 'UP' }))

    await expect(getApiHealth()).resolves.toEqual({ status: 'UP' })
  })

  it('trata resposta com status desconhecido como UNKNOWN, sem quebrar', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(respostaCom({ status: 'ALGO_NOVO' }))

    await expect(getApiHealth()).resolves.toEqual({ status: 'UNKNOWN' })
  })

  it('trata corpo inesperado como UNKNOWN, sem quebrar', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(respostaCom('<html>proxy</html>'))

    await expect(getApiHealth()).resolves.toEqual({ status: 'UNKNOWN' })
  })

  it('aceita 503 como resposta válida e só 200 e 503', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(respostaCom({ status: 'DOWN' }, 503))

    await expect(getApiHealth()).resolves.toEqual({ status: 'DOWN' })

    // O Actuator responde 503 quando o banco está fora. Isso é "API no ar, com problema", e não falha
    // de rede. Qualquer outro status continua sendo tratado como erro.
    const config = get.mock.calls[0]?.[1] as AxiosRequestConfig
    const validateStatus = config.validateStatus
    expect(validateStatus?.(200)).toBe(true)
    expect(validateStatus?.(503)).toBe(true)
    expect(validateStatus?.(500)).toBe(false)
    expect(validateStatus?.(401)).toBe(false)
  })

  it('propaga falha de rede para que a tela mostre o erro', async () => {
    vi.spyOn(http, 'get').mockRejectedValue(new Error('Network Error'))

    await expect(getApiHealth()).rejects.toThrow('Network Error')
  })
})
