export type HealthResponse = {
  code: number
  message: string
  data: {
    status: string
    service: string
    timestamp: string
  } | null
}

export type ApiResponse<T> = {
  code: number
  message: string
  data: T | null
}

export type PageData<T> = {
  items: T[]
  page: number
  size: number
  total: number
}

export type NoticeKind = 'info' | 'error'

export type PracticeNotice = {
  kind: NoticeKind
  title: string
  message: string
}
