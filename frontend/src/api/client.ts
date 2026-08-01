import type { ApiResponse, HealthResponse } from '../types/api'

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
  return error instanceof Error ? error.message : '请求失败'
}

export async function fetchHealth() {
  const response = await fetch('/api/health')
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.json() as Promise<HealthResponse>
}
