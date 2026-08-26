import { useEffect, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { fetchErrorTypes as queryErrorTypes } from '../api/errorTypeApi'
import type { ErrorType, ErrorTypeEnabledFilter, ErrorTypeFilterState, ErrorTypeLevelFilter } from '../types/errorType'
import { useLanguage } from '../i18n/LanguageContext'

const INITIAL_FILTERS: ErrorTypeFilterState = {
  typeLevel: '',
  parentId: '',
  enabled: '',
  page: 1,
  size: 20,
}

export default function ErrorTypeManagementPage() {
  const { english, text } = useLanguage()
  const [errorTypes, setErrorTypes] = useState<ErrorType[]>([])
  const [total, setTotal] = useState(0)
  const [filters, setFilters] = useState<ErrorTypeFilterState>(INITIAL_FILTERS)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    async function fetchErrorTypes() {
      setLoading(true)
      setError(null)

      try {
        const result = await queryErrorTypes(filters, controller.signal)
        setErrorTypes(result.items)
        setTotal(result.total)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setErrorTypes([])
        setTotal(0)
        setError(getErrorMessage(fetchError))
      } finally {
        setLoading(false)
      }
    }

    fetchErrorTypes()

    return () => {
      controller.abort()
    }
  }, [filters])

  const totalPages = Math.max(Math.ceil(total / filters.size), 1)
  const firstItemNo = total === 0 ? 0 : (filters.page - 1) * filters.size + 1
  const lastItemNo = Math.min(filters.page * filters.size, total)

  function updateFilters(patch: Partial<ErrorTypeFilterState>) {
    setFilters((current) => ({
      ...current,
      ...patch,
      page: patch.page ?? 1,
    }))
  }

  function refreshErrorTypes() {
    setFilters((current) => ({ ...current }))
  }

  return (
    <section className="page-content" aria-label="error type management page">
      <section className="surface error-type-management-panel" aria-label="error type query">
        <form className="error-type-filter-bar" onSubmit={(event) => event.preventDefault()}>
          <label>
            <span>{text('类型层级', 'Type level')}</span>
            <select
              value={filters.typeLevel}
              onChange={(event) => updateFilters({ typeLevel: event.target.value as ErrorTypeLevelFilter })}
            >
              <option value="">{text('全部', 'All')}</option>
              <option value="1">{text('一级分类', 'Primary category')}</option>
              <option value="2">{text('二级分类', 'Secondary category')}</option>
            </select>
          </label>

          <label>
            <span>{text('父级 ID', 'Parent ID')}</span>
            <input
              inputMode="numeric"
              pattern="[0-9]*"
              placeholder={text('不限制', 'Any')}
              value={filters.parentId}
              onChange={(event) => updateFilters({ parentId: event.target.value.replace(/\D/g, '') })}
            />
          </label>

          <label>
            <span>{text('启用状态', 'Status')}</span>
            <select
              value={filters.enabled}
              onChange={(event) => updateFilters({ enabled: event.target.value as ErrorTypeEnabledFilter })}
            >
              <option value="">{text('全部', 'All')}</option>
              <option value="true">{text('启用', 'Enabled')}</option>
              <option value="false">{text('停用', 'Disabled')}</option>
            </select>
          </label>
        </form>

        {error ? (
          <div className="notice is-error">
            <strong>{text('错误类型加载失败', 'Could not load error types')}</strong>
            <p>{error}</p>
          </div>
        ) : null}

        <div className="table-wrap">
          <table className="responsive-list-table error-type-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>{text('层级', 'Level')}</th>
                <th>{text('父级', 'Parent')}</th>
                <th>{text('编码', 'Code')}</th>
                <th>{text('名称', 'Name')}</th>
                <th>{text('说明', 'Description')}</th>
                <th>{text('排序', 'Order')}</th>
                <th>{text('状态', 'Status')}</th>
                <th>{text('更新时间', 'Updated at')}</th>
              </tr>
            </thead>
            <tbody>
              {errorTypes.map((errorType) => (
                <tr key={errorType.id}>
                  <td data-label="ID">{errorType.id}</td>
                  <td data-label={text('层级', 'Level')}>{formatTypeLevel(errorType.typeLevel, english)}</td>
                  <td data-label={text('父级', 'Parent')}>{errorType.parentId ?? '-'}</td>
                  <td className="code-cell table-ellipsis-cell" data-label={text('编码', 'Code')} title={errorType.code}>{errorType.code}</td>
                  <td className="table-ellipsis-cell" data-label={text('名称', 'Name')} title={english ? errorType.nameEn : errorType.name}>{english ? errorType.nameEn : errorType.name}</td>
                  <td className="table-ellipsis-cell" data-label={text('说明', 'Description')} title={(english ? errorType.descriptionEn : errorType.description) ?? undefined}>{(english ? errorType.descriptionEn : errorType.description) ?? '-'}</td>
                  <td data-label={text('排序', 'Order')}>{errorType.sortOrder}</td>
                  <td data-label={text('状态', 'Status')}>{errorType.enabled ? text('启用', 'Enabled') : text('停用', 'Disabled')}</td>
                  <td data-label={text('更新时间', 'Updated')}>{formatDateTime(errorType.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {!loading && errorTypes.length === 0 ? <p className="empty-state">{text('暂无错误类型数据', 'No error types')}</p> : null}
        </div>

        <div className="pagination-bar">
          <div className="pagination-summary">
            <span>
              {loading ? text('加载中', 'Loading') : text(`第 ${filters.page} / ${totalPages} 页 · ${firstItemNo}-${lastItemNo} / ${total}`, `Page ${filters.page} / ${totalPages} · ${firstItemNo}-${lastItemNo} / ${total}`)}
            </span>
            <label className="page-size-field">
              <span>{text('每页数量', 'Page size')}</span>
              <select value={filters.size} onChange={(event) => updateFilters({ size: Number(event.target.value) })}>
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
              disabled={filters.page <= 1 || loading}
              onClick={() => updateFilters({ page: filters.page - 1 })}
            >
              {text('上一页', 'Previous')}
            </button>
            <button
              type="button"
              disabled={filters.page >= totalPages || loading}
              onClick={() => updateFilters({ page: filters.page + 1 })}
            >
              {text('下一页', 'Next')}
            </button>
            <button type="button" disabled={loading} onClick={refreshErrorTypes}>
              {text('刷新', 'Refresh')}
            </button>
          </div>
        </div>
      </section>
    </section>
  )
}

function formatTypeLevel(value: 1 | 2, english: boolean) {
  return value === 1 ? (english ? 'Primary category' : '一级分类') : (english ? 'Secondary category' : '二级分类')
}

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
