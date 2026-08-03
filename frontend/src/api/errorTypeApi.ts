import { readApiResponse } from './client'
import type { ErrorTypeFilterState, ErrorTypePageData } from '../types/errorType'

export async function fetchErrorTypes(
  filters: ErrorTypeFilterState,
  signal?: AbortSignal,
): Promise<ErrorTypePageData> {
  const searchParams = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size),
  })

  if (filters.typeLevel) {
    searchParams.set('typeLevel', filters.typeLevel)
  }
  if (filters.parentId) {
    searchParams.set('parentId', filters.parentId)
  }
  if (filters.enabled) {
    searchParams.set('enabled', filters.enabled)
  }

  const response = await fetch(`/api/error-types?${searchParams.toString()}`, { signal })
  const result = await readApiResponse<ErrorTypePageData>(response)
  return result.data ?? { items: [], page: filters.page, size: filters.size, total: 0 }
}
