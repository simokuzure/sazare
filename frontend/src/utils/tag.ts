import type { Tag } from '../types/tag'

export function getTagDisplayName(tag: Pick<Tag, 'name' | 'nameEn'>, english: boolean) {
  return english ? tag.nameEn : tag.name
}
