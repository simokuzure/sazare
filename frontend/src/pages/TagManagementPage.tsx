import { useEffect, useState } from 'react'
import { fetchTags as queryTags } from '../api/tagApi'
import PageHeader from '../components/PageHeader'
import type { Tag, TagFilter } from '../types/tag'
import { useLanguage } from '../i18n/LanguageContext'
import { getTagDisplayName } from '../utils/tag'

export default function TagManagementPage() {
  const { english, text } = useLanguage()
  const [tags, setTags] = useState<Tag[]>([])
  const [total, setTotal] = useState(0)
  const [tagType, setTagType] = useState<TagFilter>('')
  const [parentId, setParentId] = useState('')
  const [enabledOnly, setEnabledOnly] = useState(true)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [tagLoading, setTagLoading] = useState(false)
  const [tagError, setTagError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

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
        setTagError(fetchError instanceof Error ? fetchError.message : text('请求失败', 'Request failed'))
      } finally {
        setTagLoading(false)
      }
    }

    fetchTags()

    return () => {
      controller.abort()
    }
  }, [tagType, parentId, enabledOnly, page, size, reloadToken, text])

  useEffect(() => {
    setPage(1)
  }, [tagType, parentId, enabledOnly, size])

  const totalPages = Math.max(Math.ceil(total / size), 1)
  const firstItemNo = total === 0 ? 0 : (page - 1) * size + 1
  const lastItemNo = Math.min(page * size, total)

  return (
          <section className="page-content target-page" aria-label="tag management page">
            <PageHeader
              title={text('标签管理', 'Tag management')}
              description={text('查看场景、功能与体裁标签，可按类型、父级和启用状态筛选。', 'Browse scene, function, and genre tags by type, parent, or enabled state.')}
            />
            <section className="surface tag-panel target-list-panel" aria-label="tag query" aria-busy={tagLoading}>
              <form className="filter-bar" onSubmit={(event) => event.preventDefault()}>
                <label>
                  <span>{text('标签类型', 'Tag type')}</span>
                  <select value={tagType} onChange={(event) => setTagType(event.target.value as TagFilter)}>
                    <option value="">{text('全部', 'All')}</option>
                    <option value="SCENE">{text('场景', 'Scene')}</option>
                    <option value="FUNCTION">{text('功能', 'Function')}</option>
                    <option value="GENRE">{text('体裁', 'Genre')}</option>
                  </select>
                </label>

                <label>
                  <span>{text('父级 ID', 'Parent ID')}</span>
                  <input
                    inputMode="numeric"
                    pattern="[0-9]*"
                    placeholder={text('不限制', 'Any')}
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
                  <span>{text('仅启用', 'Enabled only')}</span>
                </label>

              </form>

              {tagError ? <div className="error-message" role="alert"><strong>{text('标签加载失败', 'Could not load tags')}</strong><span>{tagError}</span><button type="button" onClick={() => setReloadToken((value) => value + 1)}>{text('重试', 'Retry')}</button></div> : null}

              <div className="table-wrap">
                <table className="responsive-list-table tag-table">
                  <caption className="sr-only">{text('标签列表', 'Tag list')}</caption>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>{text('类型', 'Type')}</th>
                      <th>{text('父级', 'Parent')}</th>
                      <th>{text('编码', 'Code')}</th>
                      <th>{text('名称', 'Name')}</th>
                      <th>{text('说明', 'Description')}</th>
                      <th>{text('排序', 'Order')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tags.map((tag) => (
                      <tr key={tag.id}>
                        <td data-label="ID">{tag.id}</td>
                        <td data-label={text('类型', 'Type')}>{formatTagType(tag.tagType, english)}</td>
                        <td data-label={text('父级', 'Parent')}>{tag.parentId ?? '-'}</td>
                        <td className="code-cell table-ellipsis-cell" data-label={text('编码', 'Code')} title={tag.code}>{tag.code}</td>
                        <td className="table-ellipsis-cell" data-label={text('名称', 'Name')} title={getTagDisplayName(tag, english)}>{getTagDisplayName(tag, english)}</td>
                        <td className="table-ellipsis-cell" data-label={text('说明', 'Description')} title={(english ? tag.descriptionEn : tag.description) ?? undefined}>{(english ? tag.descriptionEn : tag.description) ?? '-'}</td>
                        <td data-label={text('排序', 'Order')}>{tag.sortOrder}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {!tagLoading && tags.length === 0 ? <p className="empty-state" role="status">{text('暂无符合当前条件的标签，请调整筛选条件后重试。', 'No tags match the current filters. Adjust the filters and try again.')}</p> : null}
              </div>

              <div className="pagination-bar">
                <div className="pagination-summary">
                  <span>
                    {tagLoading ? text('加载中', 'Loading') : text(`第 ${page} / ${totalPages} 页 · ${firstItemNo}-${lastItemNo} / ${total}`, `Page ${page} / ${totalPages} · ${firstItemNo}-${lastItemNo} / ${total}`)}
                  </span>
                  <label className="page-size-field">
                    <span>{text('每页数量', 'Page size')}</span>
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
                    {text('上一页', 'Previous')}
                  </button>
                  <button
                    type="button"
                    disabled={page >= totalPages || tagLoading}
                    onClick={() => setPage((value) => value + 1)}
                  >
                    {text('下一页', 'Next')}
                  </button>
                </div>
              </div>
            </section>
          </section>
  )
}

function formatTagType(tagType: Tag['tagType'], english: boolean) {
  if (tagType === 'SCENE') return english ? 'Scene' : '场景'
  if (tagType === 'GENRE') return english ? 'Genre' : '体裁'
  return english ? 'Function' : '功能'
}
