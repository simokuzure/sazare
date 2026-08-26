export type LearningStatisticsRange = 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'LAST_90_DAYS' | 'CUSTOM'

export type LearningStatisticsFilters = {
  learningMode?: 'ZH_TO_JA' | 'EN_TO_JA'
  range: LearningStatisticsRange
  startDate?: string
  endDate?: string
}

export type LearningStatistics = {
  period: {
    range: LearningStatisticsRange
    startDate: string
    endDate: string
  }
  overview: {
    answerCount: number
    reviewedAnswerCount: number
    averageTotalScore: number | null
    confirmedErrorCount: number
  }
  dailyTrends: Array<{
    date: string
    answerCount: number
    averageTotalScore: number | null
  }>
  scoreDimensions: {
    grammarVocabularyScore: number | null
    naturalFluencyScore: number | null
    scenarioAdaptationScore: number | null
    informationCompletenessScore: number | null
  }
  weaknesses: Array<{
    userErrorTypeId: number
    userErrorTypeName: string
    userErrorTypeStatus: 'ACTIVE' | 'ARCHIVED'
    errorTypeId: number
    errorTypeCode: string
    errorTypeName: string
    confirmedCount: number
    lowSeverityCount: number
    mediumSeverityCount: number
    highSeverityCount: number
    lastConfirmedAt: string
    reviewState: 'NOT_CREATED' | 'DUE' | 'ACTIVE' | 'MASTERED'
  }>
  reviewOverview: {
    dueCardCount: number
    activeCardCount: number
    masteredCardCount: number
    periodReviewAttemptCount: number
    periodReviewPassCount: number
    periodReviewPassRate: number | null
    periodCompletedCycleCount: number
  }
  correctionOverview: {
    answerCount: number
    reviewedAnswerCount: number
    averageTotalScore: number | null
    confirmedErrorCount: number
  }
  correctionDailyTrends: Array<{
    date: string
    answerCount: number
    averageTotalScore: number | null
  }>
  correctionScoreDimensions: {
    grammarVocabularyScore: number | null
    naturalFluencyScore: number | null
    scenarioAdaptationScore: number | null
    informationCompletenessScore: number | null
  }
}
