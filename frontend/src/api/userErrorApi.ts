import { readApiResponse } from './client'
import type { PageData } from '../types/api'
import type {
  ReviewCardCreatePayload,
  ReviewCardCreated,
  UserAnswerError,
  UserAnswerErrorConfirmationPayload,
  UserErrorType,
  UserErrorTypeFilterState,
} from '../types/userError'

export async function fetchUserErrorTypes(
  filters: UserErrorTypeFilterState,
  signal?: AbortSignal,
): Promise<PageData<UserErrorType>> {
  const searchParams = new URLSearchParams({
    status: filters.status,
    page: String(filters.page),
    size: String(filters.size),
  })
  const response = await fetch(`/api/user-error-types?${searchParams.toString()}`, { signal })
  const result = await readApiResponse<PageData<UserErrorType>>(response)
  return result.data ?? { items: [], page: filters.page, size: filters.size, total: 0 }
}

export async function confirmUserAnswerErrors(
  userAnswerId: number,
  payload: UserAnswerErrorConfirmationPayload,
): Promise<UserAnswerError[]> {
  const response = await fetch(`/api/user-answers/${userAnswerId}/errors`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const result = await readApiResponse<UserAnswerError[]>(response)
  return result.data ?? []
}

export async function createReviewCard(
  userAnswerId: number,
  payload: ReviewCardCreatePayload,
): Promise<ReviewCardCreated> {
  const response = await fetch(`/api/user-answers/${userAnswerId}/review-cards`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const result = await readApiResponse<ReviewCardCreated>(response)
  if (!result.data) throw new Error('创建复习卡片未返回结果')
  return result.data
}
