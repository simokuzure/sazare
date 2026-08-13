import type { AnswerScores } from './review'
import type { QuestionAnswer } from './question'
import type { Tag } from './tag'
import type { QuestionType } from './question'

export type AnswerStatus = '' | 'SUBMITTED' | 'REVIEWED' | 'FAILED'

export type UserAnswerFilterState = {
  answerStatus: AnswerStatus
  questionType: '' | QuestionType
  questionId: string
  level: string
  minTotalScore: string
  maxTotalScore: string
  page: number
  size: number
}

export type UserAnswerRecord = {
  id: number
  questionId: number
  questionType: QuestionType
  sourceText: string
  level: string | null
  difficulty: number | null
  answerText: string
  answerStatus: Exclude<AnswerStatus, ''>
  scores: {
    [Key in keyof AnswerScores]: AnswerScores[Key] | null
  }
  totalScore: number | null
  createdAt: string
  updatedAt: string
}

export type UserAnswerDetail = UserAnswerRecord & {
  contextText: string
  grammarPoint: string
  tags: Tag[]
  answers: QuestionAnswer[]
  overallComment: string | null
}
