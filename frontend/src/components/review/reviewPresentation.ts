import type { ReviewCardListMode } from '../../types/review'

export function listModeLabel(mode: ReviewCardListMode) {
  if (mode === 'DUE') return '待复习'
  if (mode === 'ACTIVE') return '全部进行中'
  return '已掌握'
}

export function emptyListText(mode: ReviewCardListMode) {
  if (mode === 'DUE') return '当前没有到期卡片。'
  if (mode === 'ACTIVE') return '当前没有进行中的复习卡片。'
  return '目前还没有已掌握卡片。'
}

export function dueText(value: string | null, text: (zh: string, en: string) => string) {
  if (!value) return '-'
  return isDue(value) ? text(`到期于 ${formatDateTime(value)}`, `Due ${formatDateTime(value)}`) : text(`下次 ${formatDateTime(value)}`, `Next ${formatDateTime(value)}`)
}

export function isDue(value: string | null) {
  if (!value) return false
  const time = new Date(value).getTime()
  return Number.isFinite(time) && time <= Date.now()
}

export function formatDateTime(value: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}
