export type TagType = 'SCENE' | 'FUNCTION' | 'GENRE'

export type TagFilter = '' | TagType

export type Tag = {
  id: number
  tagType: TagType
  parentId: number | null
  code: string
  name: string
  description: string | null
  nameEn: string
  descriptionEn: string | null
  sortOrder: number
}
