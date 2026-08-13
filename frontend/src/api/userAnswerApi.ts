import { readApiResponse } from './client'
import type { PageData } from '../types/api'
import type { UserAnswerDetail, UserAnswerFilterState, UserAnswerRecord } from '../types/userAnswer'

export async function fetchUserAnswers(
  filters: UserAnswerFilterState,
  signal?: AbortSignal,
): Promise<PageData<UserAnswerRecord>> {
  const searchParams = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size),
  })

  if (filters.answerStatus) {
    searchParams.set('answerStatus', filters.answerStatus)
  }
  if (filters.questionType) {
    searchParams.set('questionType', filters.questionType)
  }
  if (filters.questionId) {
    searchParams.set('questionId', filters.questionId)
  }
  if (filters.level) {
    searchParams.set('level', filters.level)
  }
  if (filters.minTotalScore.trim()) {
    searchParams.set('minTotalScore', filters.minTotalScore.trim())
  }
  if (filters.maxTotalScore.trim()) {
    searchParams.set('maxTotalScore', filters.maxTotalScore.trim())
  }

  const response = await fetch(`/api/user-answers?${searchParams.toString()}`, { signal })
  const result = await readApiResponse<PageData<UserAnswerRecord>>(response)
  return result.data ?? { items: [], page: filters.page, size: filters.size, total: 0 }
}

export async function fetchUserAnswerDetail(id: number, signal?: AbortSignal): Promise<UserAnswerDetail> {
  const response = await fetch(`/api/user-answers/${id}`, { signal })
  const result = await readApiResponse<UserAnswerDetail>(response)
  if (!result.data) {
    throw new Error('后端没有返回答题记录详情')
  }
  return result.data
}
