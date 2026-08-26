import { useMemo } from 'react'
import type { Tag } from '../types/tag'
import { getTagDisplayName } from '../utils/tag'

interface TagCascadeSelectProps {
  tags: Tag[]
  english: boolean
  loading?: boolean
  parentId: string
  tagCode: string
  parentLabel: string
  childLabel: string
  parentPlaceholder: string
  childPlaceholder: string
  selectParentFirstText: string
  onParentChange: (parentId: string) => void
  onTagChange: (tagCode: string) => void
}

export default function TagCascadeSelect({
  tags,
  english,
  loading = false,
  parentId,
  tagCode,
  parentLabel,
  childLabel,
  parentPlaceholder,
  childPlaceholder,
  selectParentFirstText,
  onParentChange,
  onTagChange,
}: TagCascadeSelectProps) {
  const parentTags = useMemo(() => tags.filter((tag) => tag.parentId === null), [tags])
  const childTags = useMemo(
    () => parentId ? tags.filter((tag) => tag.parentId === Number(parentId)) : [],
    [parentId, tags],
  )

  return (
    <>
      <label>
        <span>{parentLabel}</span>
        <select
          value={parentId}
          disabled={loading}
          onChange={(event) => {
            onParentChange(event.target.value)
            onTagChange('')
          }}
        >
          <option value="">{parentPlaceholder}</option>
          {parentTags.map((tag) => (
            <option key={tag.id} value={tag.id}>{getTagDisplayName(tag, english)}</option>
          ))}
        </select>
      </label>
      <label>
        <span>{childLabel}</span>
        <select
          value={tagCode}
          disabled={loading || !parentId}
          onChange={(event) => onTagChange(event.target.value)}
        >
          <option value="">{parentId ? childPlaceholder : selectParentFirstText}</option>
          {childTags.map((tag) => (
            <option key={tag.id} value={tag.code}>{getTagDisplayName(tag, english)}</option>
          ))}
        </select>
      </label>
    </>
  )
}
