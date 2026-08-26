import type { ApiResponse, HealthResponse } from '../types/api'
import { LEARNING_MODE_STORAGE_KEY } from '../i18n/translationDirections'

export async function readApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  let result: ApiResponse<T> | null = null

  try {
    result = (await response.json()) as ApiResponse<T>
  } catch {
    throw new Error(response.ok ? '响应不是合法 JSON' : `HTTP ${response.status}`)
  }

  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `HTTP ${response.status}`)
  }

  return result
}

export function getErrorMessage(error: unknown) {
  if (window.localStorage.getItem(LEARNING_MODE_STORAGE_KEY) === 'EN_TO_JA') {
    if (error instanceof TypeError) return 'Network connection failed.'
    if (error instanceof Error && /^HTTP \d+$/.test(error.message)) return `Request failed (${error.message}).`
    return 'The request could not be completed. Please try again.'
  }
  return error instanceof Error ? error.message : '请求失败'
}

export async function fetchHealth() {
  const response = await fetch('/api/health')
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.json() as Promise<HealthResponse>
}
