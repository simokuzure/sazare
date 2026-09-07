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

export type ErrorConfirmationBuildResult =
  | {
      ok: true
      payload: UserAnswerErrorConfirmation[]
      selectedIndexes: number[]
    }
  | {
      ok: false
      reason: 'MISSING_NEW_CARD_DETAILS' | 'MISSING_EXISTING_CARD'
    }

export function toErrorCandidateState(analysis: AnswerErrorAnalysis): ErrorCandidateState {
  return { selected: false, saved: false, mode: 'NEW_USER_ERROR_TYPE', userErrorTypeName: analysis.suggestedUserErrorTypeName, userErrorTypeDescription: analysis.suggestedUserErrorTypeDescription, userErrorTypeId: '' }
}

export function toNewErrorConfirmation(analysis: AnswerErrorAnalysis, candidate: ErrorCandidateState, sortOrder: number): UserAnswerErrorConfirmation {
  return { mode: 'NEW_USER_ERROR_TYPE', errorTypeId: analysis.errorTypeId, userErrorTypeName: candidate.userErrorTypeName.trim(), userErrorTypeDescription: candidate.userErrorTypeDescription.trim(), originalText: analysis.original, issue: analysis.issue, suggestion: analysis.suggestion, reviewSourceText: analysis.reviewSourceText ?? undefined, severity: analysis.severity, sortOrder }
}

export function toExistingErrorConfirmation(analysis: AnswerErrorAnalysis, candidate: ErrorCandidateState, sortOrder: number): UserAnswerErrorConfirmation {
  return { mode: 'EXISTING_USER_ERROR_TYPE', userErrorTypeId: Number(candidate.userErrorTypeId), originalText: analysis.original, issue: analysis.issue, suggestion: analysis.suggestion, reviewSourceText: analysis.reviewSourceText ?? undefined, severity: analysis.severity, sortOrder }
}

export function buildErrorConfirmations(
  analyses: AnswerErrorAnalysis[],
  candidates: ErrorCandidateState[],
): ErrorConfirmationBuildResult {
  const payload: UserAnswerErrorConfirmation[] = []
  const selectedIndexes: number[] = []

  for (const [index, analysis] of analyses.entries()) {
    const candidate = candidates[index]
    if (!candidate?.selected || candidate.saved) continue

    if (candidate.mode === 'NEW_USER_ERROR_TYPE') {
      if (!candidate.userErrorTypeName.trim() || !candidate.userErrorTypeDescription.trim()) {
        return { ok: false, reason: 'MISSING_NEW_CARD_DETAILS' }
      }
      payload.push(toNewErrorConfirmation(analysis, candidate, index))
    } else {
      if (!candidate.userErrorTypeId) {
        return { ok: false, reason: 'MISSING_EXISTING_CARD' }
      }
      payload.push(toExistingErrorConfirmation(analysis, candidate, index))
    }

    selectedIndexes.push(index)
  }

  return { ok: true, payload, selectedIndexes }
}
