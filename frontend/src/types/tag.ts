export type TagFilter = '' | 'SCENE' | 'FUNCTION'

export type Tag = {
  id: number
  tagType: 'SCENE' | 'FUNCTION'
  parentId: number | null
  code: string
  name: string
  description: string | null
  sortOrder: number
}
