import type { QuestionType } from '../types/question'

export type LearningMode = 'ZH_TO_JA' | 'EN_TO_JA'
export type UiLanguage = 'zh-CN' | 'en-US'
export const LEARNING_MODE_STORAGE_KEY = 'sazare.learningMode'

export type TranslationDirection = {
  learningMode: LearningMode
  language: UiLanguage
  sourceLanguage: 'Chinese' | 'English'
  shortQuestionType: Extract<QuestionType, 'TRANSLATION_ZH_TO_JA' | 'TRANSLATION_EN_TO_JA'>
  articleQuestionType: Extract<QuestionType, 'TRANSLATION_ZH_TO_JA_ARTICLE' | 'TRANSLATION_EN_TO_JA_ARTICLE'>
}

export const TRANSLATION_DIRECTIONS: Record<LearningMode, TranslationDirection> = {
  ZH_TO_JA: {
    learningMode: 'ZH_TO_JA',
    language: 'zh-CN',
    sourceLanguage: 'Chinese',
    shortQuestionType: 'TRANSLATION_ZH_TO_JA',
    articleQuestionType: 'TRANSLATION_ZH_TO_JA_ARTICLE',
  },
  EN_TO_JA: {
    learningMode: 'EN_TO_JA',
    language: 'en-US',
    sourceLanguage: 'English',
    shortQuestionType: 'TRANSLATION_EN_TO_JA',
    articleQuestionType: 'TRANSLATION_EN_TO_JA_ARTICLE',
  },
}
