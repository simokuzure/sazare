import { readApiResponse } from './client'
import type { LearningStatistics, LearningStatisticsFilters } from '../types/learningStatistics'

export async function fetchLearningStatistics(
  filters: LearningStatisticsFilters,
  signal?: AbortSignal,
): Promise<LearningStatistics> {
  const searchParams = new URLSearchParams({
    range: filters.range,
    learningMode: filters.learningMode ?? 'ZH_TO_JA',
  })
  if (filters.range === 'CUSTOM') {
    searchParams.set('startDate', filters.startDate ?? '')
    searchParams.set('endDate', filters.endDate ?? '')
  }

  const response = await fetch(`/api/learning-statistics?${searchParams.toString()}`, { signal })
  const result = await readApiResponse<LearningStatistics>(response)
  if (!result.data) {
    throw new Error('后端没有返回学习统计数据')
  }
  return result.data
}
