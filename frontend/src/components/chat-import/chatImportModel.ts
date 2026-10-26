import type { ChatImportCandidate, ChatImportDraft, ChatImportItem } from '../../types/chatImport'
import type { ErrorType } from '../../types/errorType'
import type { ReviewCard } from '../../types/review'

export const MAX_CHAT_FILE_BYTES = 128 * 1024
export const UNSUPPORTED_IMPORT_CODES = new Set(['OMISSION', 'MISTRANSLATION', 'ADDITION', 'FALSE_FRIEND', 'CHINESE_CALQUE'])
export type DraftField = 'errorTypeId' | 'name' | 'description' | 'userErrorTypeId' | 'reviewSourceText' | 'referenceAnswer'
export type DraftErrors = Partial<Record<DraftField, string>>

export function createImportDraft(candidate: ChatImportCandidate, key: number): ChatImportDraft {
  return { ...candidate, key, selected: false, saved: false, mode: 'NEW_USER_ERROR_TYPE', userErrorTypeId: '' }
}

export function validateImportDraft(draft: ChatImportDraft, mode: string, types: ErrorType[], cards: ReviewCard[]): DraftErrors {
  const errors: DraftErrors = {}
  if (draft.mode === 'NEW_USER_ERROR_TYPE') {
    if (!types.some(type => type.id === draft.errorTypeId)) errors.errorTypeId = '请选择可用的错误分类。'
    if (!draft.name.trim() || draft.name.length > 128) errors.name = '请填写名称（最多 128 字符）。'
    if (!draft.description.trim() || draft.description.length > 255) errors.description = '请填写说明（最多 255 字符）。'
  } else if (!cards.some(card => card.userErrorTypeId === Number(draft.userErrorTypeId)
    && types.some(type => type.id === card.errorTypeId))) {
    errors.userErrorTypeId = '请选择可用的已有复习卡片。'
  }
  const source = draft.reviewSourceText.trim()
  if (!source || source.length > 1000) errors.reviewSourceText = '请填写题面（最多 1000 字符）。'
  else if (/[\p{Script=Hiragana}\p{Script=Katakana}]/u.test(source)
    || (mode === 'ZH_TO_JA' ? !/\p{Script=Han}/u.test(source) : /\p{Script=Han}/u.test(source) || !/[A-Za-z]/.test(source))) {
    errors.reviewSourceText = mode === 'ZH_TO_JA' ? '请使用中文，不含日语假名。' : '请使用英文，不含中文或日语假名。'
  }
  const answer = draft.referenceAnswer.trim()
  if (!answer || answer.length > 2000) errors.referenceAnswer = '请填写日语答案（最多 2000 字符）。'
  else if (!/[\p{Script=Hiragana}\p{Script=Katakana}]/u.test(answer)) errors.referenceAnswer = '参考答案需要包含日语假名。'
  return errors
}

export function toImportItem(draft: ChatImportDraft): ChatImportItem {
  return {
    mode: draft.mode, severity: draft.severity,
    reviewSourceText: draft.reviewSourceText.trim(), referenceAnswer: draft.referenceAnswer.trim(),
    ...(draft.mode === 'NEW_USER_ERROR_TYPE'
      ? { errorTypeId: draft.errorTypeId, name: draft.name.trim(), description: draft.description.trim() }
      : { userErrorTypeId: Number(draft.userErrorTypeId) }),
  }
}

export function matchingImportCard(draft: ChatImportDraft, cards: ReviewCard[]) {
  return draft.mode === 'EXISTING_USER_ERROR_TYPE'
    ? cards.find(card => card.userErrorTypeId === Number(draft.userErrorTypeId))
    : cards.find(card => card.errorTypeId === draft.errorTypeId && card.userErrorTypeName === draft.name.trim())
}

export function importTargetCount(drafts: ChatImportDraft[], cards: ReviewCard[]) {
  return new Set(drafts.map(draft => {
    const card = matchingImportCard(draft, cards)
    return card ? `existing:${card.userErrorTypeId}` : `new:${draft.errorTypeId}:${draft.name.trim()}`
  })).size
}
