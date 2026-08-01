import type { Tag } from './tag'

export type QuestionAnswer = {
  id: number
  answerText: string
  answerType: 'STANDARD' | 'REFERENCE'
  primaryAnswer: boolean
  sortOrder: number
}

export type Question = {
  id: number
  questionType: 'TRANSLATION_ZH_TO_JA'
  sourceText: string
  contextText: string
  level: string
  difficulty: number
  grammarPoint: string
  spoken: boolean
  business: boolean
  exam: boolean
  sourceType: 'AI' | 'MANUAL'
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
  level: string
  difficulty: string
  tagCodes: string
  sourceType: '' | 'AI' | 'MANUAL'
  enabled: 'true' | 'false' | 'all'
  page: number
  size: number
}

export type AiQuestionGenerationPayload = {
  questionCount?: number
  level?: string
  difficulty?: number
  sceneTagCodes?: string[]
  functionTagCodes?: string[]
  excludedSourceTexts?: string[]
  extraRequirements?: string
}

export type QuestionPayload = {
  questionType: 'TRANSLATION_ZH_TO_JA'
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
