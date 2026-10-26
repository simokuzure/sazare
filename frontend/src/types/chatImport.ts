import type { LearningMode } from '../i18n/translationDirections'
import type { ErrorConfirmationMode } from './userError'

export type LeaveGuard = () => boolean
export type LeaveGuardListener = (guard: LeaveGuard | null) => void
export type ImportSeverity = 'LOW' | 'MEDIUM' | 'HIGH'

export type ChatImportCandidate = {
  errorTypeId: number
  name: string
  description: string
  severity: ImportSeverity
  evidence: Array<{ messageId: number; originalText: string }>
  correctedExpression: string
  reviewSourceText: string
  referenceAnswer: string
}

export type ChatImportAnalysis = {
  checkedUserMessageIds: number[]
  candidates: ChatImportCandidate[]
}

export type ChatImportDraft = ChatImportCandidate & {
  key: number
  selected: boolean
  saved: boolean
  mode: ErrorConfirmationMode
  userErrorTypeId: string
}

export type ChatImportItem = {
  mode: ErrorConfirmationMode
  errorTypeId?: number
  userErrorTypeId?: number
  name?: string
  description?: string
  severity: ImportSeverity
  reviewSourceText: string
  referenceAnswer: string
}

export type ChatImportPayload = { learningMode: LearningMode; items: ChatImportItem[] }
export type ChatImportCardResult = {
  cardId: number
  userErrorTypeId: number
  questionIds: number[]
  action: 'CREATED' | 'UPDATED' | 'REACTIVATED'
}
export type ChatImportConfirmation = { cards: ChatImportCardResult[] }
