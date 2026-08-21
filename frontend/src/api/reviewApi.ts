import { readApiResponse } from './client'
import type { PageData } from '../types/api'
import type {
  DerivedQuestionGenerationResult,
  ReviewAttemptPayload,
  ReviewAttemptResult,
  ReviewCard,
  ReviewCardDetail,
  ReviewCardFilterState,
} from '../types/review'

export async function fetchReviewCards(
  filters: ReviewCardFilterState,
  signal?: AbortSignal,
): Promise<PageData<ReviewCard>> {
  const searchParams = new URLSearchParams({
    status: filters.mode === 'MASTERED' ? 'MASTERED' : 'ACTIVE',
    dueOnly: String(filters.mode === 'DUE'),
    page: String(filters.page),
    size: String(filters.size),
  })
  const response = await fetch(`/api/review-cards?${searchParams.toString()}`, { signal })
  const result = await readApiResponse<PageData<ReviewCard>>(response)
  return result.data ?? { items: [], page: filters.page, size: filters.size, total: 0 }
}

export async function fetchReviewCard(
  cardId: number,
  earlyReview = false,
  signal?: AbortSignal,
): Promise<ReviewCardDetail> {
  const searchParams = new URLSearchParams({ earlyReview: String(earlyReview) })
  const response = await fetch(`/api/review-cards/${cardId}?${searchParams.toString()}`, { signal })
  const result = await readApiResponse<ReviewCardDetail>(response)
  if (!result.data) {
    throw new Error('后端没有返回复习卡片详情')
  }
  return result.data
}

export async function deleteReviewCard(cardId: number): Promise<void> {
  const response = await fetch(`/api/review-cards/${cardId}`, { method: 'DELETE' })
  await readApiResponse<null>(response)
}

export async function submitReviewAttempt(
  cardId: number,
  payload: ReviewAttemptPayload,
): Promise<ReviewAttemptResult> {
  const response = await fetch(`/api/review-cards/${cardId}/attempts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const result = await readApiResponse<ReviewAttemptResult>(response)
  if (!result.data) {
    throw new Error('后端没有返回复习评分结果')
  }
  return result.data
}

export async function generateDerivedReviewQuestion(
  cardId: number,
): Promise<DerivedQuestionGenerationResult> {
  const response = await fetch(`/api/review-cards/${cardId}/derived-question-generations`, {
    method: 'POST',
  })
  const result = await readApiResponse<DerivedQuestionGenerationResult>(response)
  if (!result.data) {
    throw new Error('后端没有返回衍生题生成结果')
  }
  return result.data
}
