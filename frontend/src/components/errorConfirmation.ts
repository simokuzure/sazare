import type { AnswerErrorAnalysis } from '../types/review'
import type { UserAnswerErrorConfirmation } from '../types/userError'

export type ErrorCandidateState = {
  selected: boolean
  saved: boolean
  mode: 'NEW_USER_ERROR_TYPE' | 'EXISTING_USER_ERROR_TYPE'
  userErrorTypeName: string
  userErrorTypeDescription: string
  userErrorTypeId: string
}

export function toErrorCandidateState(analysis: AnswerErrorAnalysis): ErrorCandidateState {
  return { selected: false, saved: false, mode: 'NEW_USER_ERROR_TYPE', userErrorTypeName: analysis.suggestedUserErrorTypeName, userErrorTypeDescription: analysis.suggestedUserErrorTypeDescription, userErrorTypeId: '' }
}

export function toNewErrorConfirmation(analysis: AnswerErrorAnalysis, candidate: ErrorCandidateState, sortOrder: number): UserAnswerErrorConfirmation {
  return { mode: 'NEW_USER_ERROR_TYPE', errorTypeId: analysis.errorTypeId, userErrorTypeName: candidate.userErrorTypeName.trim(), userErrorTypeDescription: candidate.userErrorTypeDescription.trim(), originalText: analysis.original, issue: analysis.issue, suggestion: analysis.suggestion, severity: analysis.severity, sortOrder }
}

export function toExistingErrorConfirmation(analysis: AnswerErrorAnalysis, candidate: ErrorCandidateState, sortOrder: number): UserAnswerErrorConfirmation {
  return { mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: Number(candidate.userErrorTypeId), originalText: analysis.original, issue: analysis.issue, suggestion: analysis.suggestion, severity: analysis.severity, sortOrder }
}
