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
  const text = (chinese: string, english: string) => mode === 'EN_TO_JA' ? english : chinese
  if (draft.mode === 'NEW_USER_ERROR_TYPE') {
    if (!types.some(type => type.id === draft.errorTypeId)) errors.errorTypeId = text('请选择可用的错误分类。', 'Select an available error category.')
    if (!draft.name.trim() || draft.name.length > 128) errors.name = text('请填写名称（最多 128 字符）。', 'Enter a name (up to 128 characters).')
    if (!draft.description.trim() || draft.description.length > 255) errors.description = text('请填写说明（最多 255 字符）。', 'Enter a description (up to 255 characters).')
  } else if (!cards.some(card => card.userErrorTypeId === Number(draft.userErrorTypeId)
    && types.some(type => type.id === card.errorTypeId))) {
    errors.userErrorTypeId = text('请选择可用的已有复习卡片。', 'Select an available existing review card.')
  }
  const source = draft.reviewSourceText.trim()
  if (!source || source.length > 1000) errors.reviewSourceText = text('请填写题面（最多 1000 字符）。', 'Enter a review prompt (up to 1000 characters).')
  else if (/[\p{Script=Hiragana}\p{Script=Katakana}]/u.test(source)
    || (mode === 'ZH_TO_JA' ? !/\p{Script=Han}/u.test(source) : /\p{Script=Han}/u.test(source) || !/[A-Za-z]/.test(source))) {
    errors.reviewSourceText = text('请使用中文，不含日语假名。', 'Use English without Chinese characters or Japanese kana.')
  }
  const answer = draft.referenceAnswer.trim()
  if (!answer || answer.length > 2000) errors.referenceAnswer = text('请填写日语答案（最多 2000 字符）。', 'Enter a Japanese answer (up to 2000 characters).')
  else if (!/[\p{Script=Hiragana}\p{Script=Katakana}]/u.test(answer)) errors.referenceAnswer = text('参考答案需要包含日语假名。', 'The reference answer must contain Japanese kana.')
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
