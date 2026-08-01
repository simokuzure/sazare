export type AnswerScores = {
  grammarVocabularyScore: number
  naturalFluencyScore: number
  scenarioAdaptationScore: number
  informationCompletenessScore: number
}

export type AnswerReviewComments = {
  grammarComment: string
  vocabularyComment: string
  naturalnessComment: string
  scenarioComment: string
}

export type AnswerErrorAnalysis = {
  type: 'GRAMMAR' | 'VOCABULARY' | 'NATURALNESS' | 'HONORIFIC' | 'SCENARIO' | 'COMPLETENESS'
  original: string
  issue: string
  suggestion: string
  severity: 'LOW' | 'MEDIUM' | 'HIGH'
}

export type AnswerRecommendedExpression = {
  expression: string
  usage: string
  formality: 'CASUAL' | 'NEUTRAL' | 'POLITE' | 'BUSINESS'
  note: string
}

export type AnswerReview = {
  userAnswerId: number
  questionId: number
  answerText: string
  answerStatus: 'SUBMITTED' | 'REVIEWED' | 'FAILED'
  scores: AnswerScores
  totalScore: number
  overallComment: string
  comments: AnswerReviewComments
  errorAnalysis: AnswerErrorAnalysis[]
  revisionSuggestions: string[]
  recommendedExpressions: AnswerRecommendedExpression[]
  createdAt: string
  updatedAt: string
}
