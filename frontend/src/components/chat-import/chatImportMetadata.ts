import { fetchErrorTypes } from '../../api/errorTypeApi'
import { fetchReviewCards } from '../../api/reviewApi'
import type { LearningMode } from '../../i18n/translationDirections'
import type { PageData } from '../../types/api'
import type { ReviewCard } from '../../types/review'
import { UNSUPPORTED_IMPORT_CODES } from './chatImportModel'

export async function collectPages<T>(fetchPage: (page: number) => Promise<PageData<T>>): Promise<T[]> {
  const items: T[] = []
  let page = 1
  while (true) {
    const result = await fetchPage(page++)
    items.push(...result.items)
    if (items.length >= result.total) return items
    if (result.items.length === 0) throw new Error('列表加载不完整，请重新加载。')
  }
}

export async function loadImportTypes(signal: AbortSignal) {
  const types = await collectPages(page => fetchErrorTypes({ typeLevel: '2', parentId: '', enabled: 'true', page, size: 100 }, signal))
  return types.filter(type => type.enabled && !UNSUPPORTED_IMPORT_CODES.has(type.code))
}

export async function loadImportCards(learningMode: LearningMode, signal: AbortSignal): Promise<ReviewCard[]> {
  const lists = await Promise.all((['ACTIVE', 'MASTERED'] as const).map(mode => collectPages(page =>
    fetchReviewCards({ mode, page, size: 100, learningMode }, signal))))
  return [...new Map(lists.flat().map(card => [card.id, card])).values()]
    .filter(card => !UNSUPPORTED_IMPORT_CODES.has(card.errorTypeCode))
}
