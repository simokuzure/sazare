import { useEffect, useState } from 'react'
import { fetchTags as queryTags } from '../api/tagApi'
import type { Tag, TagFilter } from '../types/tag'

export default function TagManagementPage() {
  const [tags, setTags] = useState<Tag[]>([])
  const [total, setTotal] = useState(0)
  const [tagType, setTagType] = useState<TagFilter>('')
  const [parentId, setParentId] = useState('')
  const [enabledOnly, setEnabledOnly] = useState(true)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [tagLoading, setTagLoading] = useState(false)
  const [tagError, setTagError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    async function fetchTags() {
      setTagLoading(true)
      setTagError(null)

      try {
        const result = await queryTags({ tagType, parentId, enabledOnly, page, size }, controller.signal)

        setTags(result.items)
        setTotal(result.total)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setTags([])
        setTotal(0)
        setTagError(fetchError instanceof Error ? fetchError.message : '请求失败')
      } finally {
        setTagLoading(false)
      }
    }

    fetchTags()

    return () => {
      controller.abort()
    }
  }, [tagType, parentId, enabledOnly, page, size])

  useEffect(() => {
    setPage(1)
  }, [tagType, parentId, enabledOnly, size])

  const totalPages = Math.max(Math.ceil(total / size), 1)
  const firstItemNo = total === 0 ? 0 : (page - 1) * size + 1
  const lastItemNo = Math.min(page * size, total)

  return (
          <section className="page-content" aria-label="tag management page">
            <section className="surface tag-panel" aria-label="tag query">
              <form className="filter-bar" onSubmit={(event) => event.preventDefault()}>
                <label>
                  <span>标签类型</span>
                  <select value={tagType} onChange={(event) => setTagType(event.target.value as TagFilter)}>
                    <option value="">全部</option>
                    <option value="SCENE">场景</option>
                    <option value="FUNCTION">功能</option>
                    <option value="GENRE">体裁</option>
                  </select>
                </label>

                <label>
                  <span>父级 ID</span>
                  <input
                    inputMode="numeric"
                    pattern="[0-9]*"
                    placeholder="不限制"
                    value={parentId}
                    onChange={(event) => setParentId(event.target.value.replace(/\D/g, ''))}
                  />
                </label>

                <label className="checkbox-field">
                  <input
                    type="checkbox"
                    checked={enabledOnly}
                    onChange={(event) => setEnabledOnly(event.target.checked)}
                  />
                  <span>仅启用</span>
                </label>

              </form>

              {tagError ? <div className="error-message">{tagError}</div> : null}

              <div className="table-wrap">
                <table className="responsive-list-table tag-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>类型</th>
                      <th>父级</th>
                      <th>编码</th>
                      <th>名称</th>
                      <th>说明</th>
                      <th>排序</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tags.map((tag) => (
                      <tr key={tag.id}>
                        <td data-label="ID">{tag.id}</td>
                        <td data-label="类型">{formatTagType(tag.tagType)}</td>
                        <td data-label="父级">{tag.parentId ?? '-'}</td>
                        <td className="code-cell table-ellipsis-cell" data-label="编码" title={tag.code}>{tag.code}</td>
                        <td className="table-ellipsis-cell" data-label="名称" title={tag.name}>{tag.name}</td>
                        <td className="table-ellipsis-cell" data-label="说明" title={tag.description ?? undefined}>{tag.description ?? '-'}</td>
                        <td data-label="排序">{tag.sortOrder}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {!tagLoading && tags.length === 0 ? <p className="empty-state">暂无标签数据</p> : null}
              </div>

              <div className="pagination-bar">
                <div className="pagination-summary">
                  <span>
                    {tagLoading ? '加载中' : `第 ${page} / ${totalPages} 页 · ${firstItemNo}-${lastItemNo} / ${total}`}
                  </span>
                  <label className="page-size-field">
                    <span>每页数量</span>
                    <select value={size} onChange={(event) => setSize(Number(event.target.value))}>
                      <option value={10}>10</option>
                      <option value={20}>20</option>
                      <option value={50}>50</option>
                      <option value={100}>100</option>
                    </select>
                  </label>
                </div>
                <div className="pagination-actions">
                  <button
                    type="button"
                    disabled={page <= 1 || tagLoading}
                    onClick={() => setPage((value) => value - 1)}
                  >
                    上一页
                  </button>
                  <button
                    type="button"
                    disabled={page >= totalPages || tagLoading}
                    onClick={() => setPage((value) => value + 1)}
                  >
                    下一页
                  </button>
                </div>
              </div>
            </section>
          </section>
  )
}

function formatTagType(tagType: Tag['tagType']) {
  if (tagType === 'SCENE') return '场景'
  if (tagType === 'GENRE') return '体裁'
  return '功能'
}
