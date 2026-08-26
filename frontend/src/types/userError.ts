import type { AnswerErrorAnalysis } from './review'

export type UserErrorTypeStatus = 'ACTIVE' | 'ARCHIVED'

export type UserErrorType = {
  id: number
  learningMode: 'ZH_TO_JA' | 'EN_TO_JA'
  errorTypeId: number
  errorTypeCode: string
  errorTypeName: string
  name: string
  description: string
  status: UserErrorTypeStatus
  createdAt: string
  updatedAt: string
}

export type UserErrorTypeFilterState = {
  learningMode?: 'ZH_TO_JA' | 'EN_TO_JA'
  status: UserErrorTypeStatus
  page: number
  size: number
}

export type ErrorConfirmationMode = 'NEW_USER_ERROR_TYPE' | 'EXISTING_USER_ERROR_TYPE'

export type UserAnswerErrorConfirmation = {
  mode: ErrorConfirmationMode
  errorTypeId?: number
  userErrorTypeId?: number
  userErrorTypeName?: string
  userErrorTypeDescription?: string
  originalText: string
  issue: string
  suggestion: string
  reviewSourceText?: string
  severity: AnswerErrorAnalysis['severity']
  sortOrder: number
}

export type UserAnswerErrorConfirmationPayload = {
  errors: UserAnswerErrorConfirmation[]
}

export type UserAnswerError = {
  id: number
  userAnswerId: number
  errorTypeId: number
  userErrorTypeId: number
  originalText: string
  issue: string
  suggestion: string
  severity: AnswerErrorAnalysis['severity']
  sortOrder: number
  createdAt: string
}

export type ReviewCardCreatePayload = {
  name: string
  targetExpression: string
  sourceSegmentIndex?: number
  reviewSourceText?: string
}

export type ReviewCardCreated = {
  id: number
  name: string
  status: 'ACTIVE'
  dueAt: string
}
