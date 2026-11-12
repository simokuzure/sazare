import type { ReviewCardListMode } from '../../types/review'

export function listModeLabel(mode: ReviewCardListMode) {
  if (mode === 'DUE') return '待复习'
  if (mode === 'ACTIVE' || mode === 'WAITING') return '进行中'
  return '已掌握'
}

export function emptyListText(mode: ReviewCardListMode) {
  if (mode === 'DUE') return '当前没有到期卡片。'
  if (mode === 'WAITING') return '当前没有等待中的复习卡片。'
  if (mode === 'ACTIVE') return '当前没有进行中的复习卡片。'
  return '目前还没有已掌握卡片。'
}

export function listModeDescription(mode: ReviewCardListMode, text: (zh: string, en: string) => string) {
  if (mode === 'DUE') return text('按到期时间从早到晚排列，同一时间优先复习本周期失败次数更多的卡片。', 'Earliest due first; ties prioritize more failures in the current cycle.')
  if (mode === 'WAITING') return text('仅显示等待下次复习的卡片，按下次到期时间从近到远排列。', 'Cards waiting for their next review, ordered by the next due time.')
  if (mode === 'MASTERED') return text('按掌握时间从近到远排列。', 'Most recently mastered first.')
  return text('按下次到期时间排列。', 'Ordered by the next due time.')
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
