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
  type: string
  errorTypeId: number
  errorTypeCode: string
  errorTypeName: string
  original: string
  issue: string
  suggestion: string
  severity: 'LOW' | 'MEDIUM' | 'HIGH'
  suggestedUserErrorTypeName: string
  suggestedUserErrorTypeDescription: string
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
