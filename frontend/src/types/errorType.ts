import type { PageData } from './api'

export type ErrorType = {
  id: number
  parentId: number | null
  typeLevel: 1 | 2
  code: string
  name: string
  description: string | null
  sortOrder: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export type ErrorTypeLevelFilter = '' | '1' | '2'
export type ErrorTypeEnabledFilter = '' | 'true' | 'false'

export type ErrorTypeFilterState = {
  typeLevel: ErrorTypeLevelFilter
  parentId: string
  enabled: ErrorTypeEnabledFilter
  page: number
  size: number
}

export type ErrorTypePageData = PageData<ErrorType>
