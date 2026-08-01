import { readApiResponse } from './client'
import type { PageData } from '../types/api'
import type { Tag, TagFilter } from '../types/tag'

export type TagQueryParams = {
  tagType?: TagFilter
  parentId?: string
  enabledOnly: boolean
  page: number
  size: number
}

export async function fetchTags(params: TagQueryParams, signal?: AbortSignal): Promise<PageData<Tag>> {
  const searchParams = new URLSearchParams()
  if (params.tagType) {
    searchParams.set('tagType', params.tagType)
  }
  if (params.parentId?.trim()) {
    searchParams.set('parentId', params.parentId.trim())
  }
  searchParams.set('enabledOnly', String(params.enabledOnly))
  searchParams.set('page', String(params.page))
  searchParams.set('size', String(params.size))

  const response = await fetch(`/api/tags?${searchParams.toString()}`, { signal })
  const result = await readApiResponse<PageData<Tag>>(response)
  return result.data ?? { items: [], page: params.page, size: params.size, total: 0 }
}
