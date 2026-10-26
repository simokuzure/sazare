import type { ApiResponse } from '../types/api'
import { LEARNING_MODE_STORAGE_KEY, type LearningMode } from '../i18n/translationDirections'
import { englishErrorMessage, REQUEST_FAILED_EN } from '../i18n/errorMessages'

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

export function getErrorMessage(error: unknown, mode?: LearningMode) {
  if ((mode ?? window.localStorage.getItem(LEARNING_MODE_STORAGE_KEY)) !== 'ZH_TO_JA') {
    if (error instanceof TypeError) return 'Network connection failed.'
    if (error instanceof Error && /^HTTP \d+$/.test(error.message)) return `Request failed (${error.message}).`
    return error instanceof Error && error.message ? englishErrorMessage(error.message) : REQUEST_FAILED_EN
  }
  return error instanceof Error ? error.message : '请求失败'
}
