// oxlint-disable react/only-export-components
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { MESSAGES, type MessageKey } from './messages'
import { LEARNING_MODE_STORAGE_KEY, TRANSLATION_DIRECTIONS, type LearningMode, type UiLanguage } from './translationDirections'

export type { LearningMode, UiLanguage } from './translationDirections'

type LanguageContextValue = {
  learningMode: LearningMode
  language: UiLanguage
  english: boolean
  setLearningMode: (mode: LearningMode) => void
  t: (key: MessageKey) => string
  text: (chinese: string, english: string) => string
  shortQuestionType: 'TRANSLATION_ZH_TO_JA' | 'TRANSLATION_EN_TO_JA'
  articleQuestionType: 'TRANSLATION_ZH_TO_JA_ARTICLE' | 'TRANSLATION_EN_TO_JA_ARTICLE'
}

const LanguageContext = createContext<LanguageContextValue | null>(null)

function initialMode(): LearningMode {
  return window.localStorage.getItem(LEARNING_MODE_STORAGE_KEY) === 'EN_TO_JA' ? 'EN_TO_JA' : 'ZH_TO_JA'
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [learningMode, setLearningMode] = useState<LearningMode>(initialMode)
  const direction = TRANSLATION_DIRECTIONS[learningMode]
  const english = direction.language === 'en-US'

  useEffect(() => {
    window.localStorage.setItem(LEARNING_MODE_STORAGE_KEY, learningMode)
    document.documentElement.lang = direction.language
    document.title = MESSAGES[direction.language].appTitle
  }, [direction.language, learningMode])

  const value = useMemo<LanguageContextValue>(() => ({
    learningMode,
    language: direction.language,
    english,
    setLearningMode,
    t: (key) => MESSAGES[direction.language][key],
    text: (chinese, englishText) => english ? englishText : chinese,
    shortQuestionType: direction.shortQuestionType,
    articleQuestionType: direction.articleQuestionType,
  }), [direction, english, learningMode])

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>
}

export function useLanguage() {
  const value = useContext(LanguageContext)
  if (!value) throw new Error('LanguageProvider is missing')
  return value
}
