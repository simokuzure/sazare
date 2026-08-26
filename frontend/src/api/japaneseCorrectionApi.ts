import { readApiResponse } from './client'
import type { JapaneseCorrectionReview } from '../types/review'

export async function correctJapanese(
  japaneseText: string,
  learningMode: 'ZH_TO_JA' | 'EN_TO_JA' = 'ZH_TO_JA',
): Promise<JapaneseCorrectionReview> {
  const response = await fetch('/api/japanese-corrections', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ japaneseText, learningMode }),
  })
  const result = await readApiResponse<JapaneseCorrectionReview>(response)
  if (!result.data) {
    throw new Error('后端没有返回日语纠错结果')
  }
  return result.data
}
