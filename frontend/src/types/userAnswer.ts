import type { AnswerScores } from './review'
import type { QuestionAnswer } from './question'
import type { Tag } from './tag'
import type { QuestionType } from './question'

export type AnswerStatus = '' | 'SUBMITTED' | 'REVIEWED' | 'FAILED'

export type UserAnswerFilterState = {
  answerStatus: AnswerStatus
  questionType: '' | QuestionType | 'JAPANESE_CORRECTION'
  questionId: string
  level: string
  minTotalScore: string
  maxTotalScore: string
  page: number
  size: number
}

export type UserAnswerRecord = {
  id: number
  questionId: number | null
  questionType: QuestionType | null
  sourceText: string | null
  level: string | null
  difficulty: number | null
  answerText: string
  answerStatus: Exclude<AnswerStatus, ''>
  scores: {
    [Key in keyof AnswerScores]: AnswerScores[Key] | null
  }
  totalScore: number | null
  revisedText: string | null
  createdAt: string
  updatedAt: string
}

export type UserAnswerDetail = UserAnswerRecord & {
  contextText: string | null
  grammarPoint: string | null
  tags: Tag[]
  answers: QuestionAnswer[]
  overallComment: string | null
}
