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

export async function fetchAllTags(
  params: Omit<TagQueryParams, 'page' | 'size'>,
  signal?: AbortSignal,
): Promise<Tag[]> {
  const pageSize = 100
  const firstPage = await fetchTags({ ...params, page: 1, size: pageSize }, signal)
  const totalPages = Math.ceil(firstPage.total / pageSize)
  if (totalPages <= 1) return firstPage.items

  const remainingPages = await Promise.all(
    Array.from({ length: totalPages - 1 }, (_, index) => fetchTags({
      ...params,
      page: index + 2,
      size: pageSize,
    }, signal)),
  )
  return [firstPage, ...remainingPages].flatMap((page) => page.items)
}
