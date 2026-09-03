import type { QuestionAnswer } from './question'
import type { Tag } from './tag'

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
  reviewSourceText?: string | null
  severity: 'LOW' | 'MEDIUM' | 'HIGH'
  suggestedUserErrorTypeName: string
  suggestedUserErrorTypeDescription: string
}

export type JapaneseCorrectionComments = {
  grammarVocabularyComment: string
  naturalFluencyComment: string
  styleConsistencyComment: string
  writingCompletenessComment: string
}

export type JapaneseCorrectionError = AnswerErrorAnalysis & {
  reviewSourceText: string
}

export type JapaneseCorrectionReview = {
  userAnswerId: number
  questionId: null
  answerText: string
  answerStatus: 'REVIEWED'
  scores: AnswerScores
  totalScore: number
  overallComment: string
  revisedText: string
  comments: JapaneseCorrectionComments
  errorAnalysis: JapaneseCorrectionError[]
  revisionSuggestions: string[]
  recommendedExpressions: AnswerRecommendedExpression[]
  createdAt: string
  updatedAt: string
}

export type AnswerRecommendedExpression = {
  expression: string
  usage: string
  formality: 'CASUAL' | 'NEUTRAL' | 'POLITE' | 'BUSINESS'
  note: string
}

export type ArticleSentenceReview = {
  sourceSegmentIndex: number
  sourceText: string
  referenceText: string
  answerExcerpt: string | null
  revisedText: string
  comment: string
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
  revisedAnswer: string | null
  sentenceReviews: ArticleSentenceReview[]
  errorAnalysis: AnswerErrorAnalysis[]
  revisionSuggestions: string[]
  recommendedExpressions: AnswerRecommendedExpression[]
  createdAt: string
  updatedAt: string
}

export type ReviewCardStatus = 'ACTIVE' | 'MASTERED'
export type ReviewState = 'WAITING' | 'READY' | 'DERIVED_GENERATION_REQUIRED' | 'MASTERED'
export type ReviewQuestionRole = 'ORIGINAL' | 'DERIVED'
export type ReviewResult = 'PASS' | 'FAIL'
export type DerivedGenerationStatus = 'NOT_REQUIRED' | 'SUCCEEDED' | 'FAILED'

export type ReviewCycleProgress = {
  cycleNo: number
  successfulReviewCount: number
  failedReviewCount: number
  netSuccessCount: number
  targetSuccessCount: number
  originalQuestionCount: number
  originalPassedCount: number
  retryQuestionCount: number
  pendingQuestionCount: number
}

export type ReviewCard = {
  id: number
  userErrorTypeId: number
  errorTypeId: number
  errorTypeCode: string
  errorTypeName: string
  userErrorTypeName: string
  userErrorTypeDescription: string
  status: ReviewCardStatus
  dueAt: string | null
  progress: ReviewCycleProgress
  lastReviewedAt: string | null
  masteredAt: string | null
}

export type ReviewQuestion = {
  cycleQuestionId: number
  questionId: number
  questionRole: ReviewQuestionRole
  sourceText: string
  contextText: string
  level: string
  difficulty: number
  grammarPoint: string
  spoken: boolean
  business: boolean
  exam: boolean
  tags: Tag[]
  attemptCount: number
}

export type ReviewAttemptHistory = {
  id: number
  cycleNo: number
  questionRole: ReviewQuestionRole
  sourceText: string
  referenceAnswer: string | null
  answerText: string
  result: ReviewResult
  totalScore: number | null
  quality: number
  createdAt: string
}

export type ReviewCardDetail = {
  id: number
  userErrorTypeId: number
  userErrorTypeName: string
  userErrorTypeDescription: string
  errorTypeId: number
  errorTypeCode: string
  errorTypeName: string
  status: ReviewCardStatus
  easeFactor: number
  repetitionCount: number
  intervalDays: number
  lapseCount: number
  dueAt: string | null
  lastReviewedAt: string | null
  masteredAt: string | null
  reviewState: ReviewState
  progress: ReviewCycleProgress | null
  currentQuestion: ReviewQuestion | null
  reviewAttempts: ReviewAttemptHistory[]
}

export type ReviewAttemptPayload = {
  cycleQuestionId: number
  answerText: string
  earlyReview?: boolean
}

export type ReviewAttemptResult = {
  userAnswerId: number
  quality: number
  result: ReviewResult
  targetErrorResolved: boolean
  feedback: string
  scores: AnswerScores
  totalScore: number
  errorAnalysis: AnswerErrorAnalysis[]
  progress: ReviewCycleProgress
  nextDueAt: string | null
  cardStatus: ReviewCardStatus
  standardAnswers: QuestionAnswer[]
  derivedGenerationStatus: DerivedGenerationStatus
}

export type ReviewCardListMode = 'DUE' | 'ACTIVE' | 'MASTERED'

export type ReviewCardFilterState = {
  learningMode?: 'ZH_TO_JA' | 'EN_TO_JA'
  mode: ReviewCardListMode
  page: number
  size: number
}

export type DerivedQuestionGenerationResult = {
  questionId: number
  cycleQuestionId: number
  status: 'SUCCEEDED'
}
