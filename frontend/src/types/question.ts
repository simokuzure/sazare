import type { Tag } from './tag'

export type QuestionType =
  | 'TRANSLATION_ZH_TO_JA'
  | 'TRANSLATION_ZH_TO_JA_ARTICLE'
  | 'TRANSLATION_EN_TO_JA'
  | 'TRANSLATION_EN_TO_JA_ARTICLE'

export type QuestionAnswer = {
  id: number
  answerText: string
  answerType: 'STANDARD' | 'REFERENCE'
  primaryAnswer: boolean
  sortOrder: number
}

export type Question = {
  id: number
  questionType: QuestionType
  sourceText: string
  contextText: string
  level: string
  difficulty: number
  grammarPoint: string
  spoken: boolean
  business: boolean
  exam: boolean
  sourceType: 'AI' | 'MANUAL' | 'REVIEW_DERIVED'
  enabled: boolean
  tags: Tag[]
  answers: QuestionAnswer[]
  createdAt: string
  updatedAt: string
}

export type QuestionFormMode = 'create' | 'edit'

export type QuestionFormState = {
  sourceText: string
  contextText: string
  level: string
  difficulty: string
  grammarPoint: string
  spoken: boolean
  business: boolean
  exam: boolean
  tagCodes: string
  standardAnswer: string
  referenceAnswers: string
}

export type QuestionFilterState = {
  questionType: QuestionType
  level: string
  difficulty: string
  tagCodes: string
  sourceType: '' | 'AI' | 'MANUAL' | 'REVIEW_DERIVED'
  enabled: 'true' | 'false' | 'all'
  page: number
  size: number
}

export type RandomQuestionFilter = {
  questionType: QuestionType
  level: string
  difficulty: string
  tagCodes: string[]
}

export type AiQuestionGenerationPayload = {
  learningMode?: 'ZH_TO_JA' | 'EN_TO_JA'
  questionCount?: number
  level?: string
  difficulty?: number
  sceneTagCodes?: string[]
  functionTagCodes?: string[]
  excludedSourceTexts?: string[]
  extraRequirements?: string
}

export type AiArticleLengthTier = 'SHORT' | 'MEDIUM' | 'LONG'

export type AiArticleGenerationPayload = {
  learningMode?: 'ZH_TO_JA' | 'EN_TO_JA'
  level?: string
  difficulty?: number
  lengthTier?: AiArticleLengthTier
  genreTagCode?: string
  topic?: string
  extraRequirements?: string
}

export type QuestionPayload = {
  questionType: QuestionType
  sourceText: string
  contextText: string
  level: string
  difficulty: number
  grammarPoint: string
  spoken: boolean
  business: boolean
  exam: boolean
  tagCodes: string[]
  answers: {
    answerText: string
    answerType: 'STANDARD' | 'REFERENCE'
    primaryAnswer: boolean
    sortOrder: number
  }[]
}
