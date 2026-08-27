export type LearningStatisticsRange = 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'LAST_90_DAYS' | 'CUSTOM'

export type LearningStatisticsFilters = {
  learningMode?: 'ZH_TO_JA' | 'EN_TO_JA'
  range: LearningStatisticsRange
  startDate?: string
  endDate?: string
}

export type LearningStatisticsPractice = {
  attemptCount: number
  averageTotalScore: number | null
  dailyTrends: Array<{
    date: string
    attemptCount: number
    averageTotalScore: number | null
  }>
  scoreDimensions: {
    grammarVocabularyScore: number | null
    naturalFluencyScore: number | null
    scenarioAdaptationScore: number | null
    informationCompletenessScore: number | null
  }
}

export type LearningStatistics = {
  checkInOverview: {
    currentStreakDays: number
    totalCheckInDays: number
  }
  period: {
    range: LearningStatisticsRange
    startDate: string
    endDate: string
  }
  translation: LearningStatisticsPractice
  correction: LearningStatisticsPractice
  reviewOverview: {
    dueCardCount: number
    inProgressCardCount: number
    masteredCardCount: number
    periodReviewAttemptCount: number
    periodReviewPassRate: number | null
  }
}
