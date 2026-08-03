import { useEffect, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { fetchUserErrorTypes } from '../api/userErrorApi'
import type { UserErrorType, UserErrorTypeStatus } from '../types/userError'

const PAGE_SIZE = 20

export default function ReviewPage() {
  const [status, setStatus] = useState<UserErrorTypeStatus>('ACTIVE')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<{ items: UserErrorType[]; total: number }>({ items: [], total: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    async function loadUserErrorTypes() {
      setLoading(true)
      setError(null)
      try {
        const result = await fetchUserErrorTypes({ status, page, size: PAGE_SIZE }, controller.signal)
        setData({ items: result.items, total: result.total })
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') return
        setData({ items: [], total: 0 })
        setError(getErrorMessage(fetchError))
      } finally {
        setLoading(false)
      }
    }
    loadUserErrorTypes()
    return () => controller.abort()
  }, [page, reloadToken, status])

  const totalPages = Math.max(1, Math.ceil(data.total / PAGE_SIZE))

  function handleStatusChange(nextStatus: UserErrorTypeStatus) {
    setStatus(nextStatus)
    setPage(1)
  }

  return (
    <section className="page-content review-page" aria-label="错题复习">
      <section className="surface review-surface">
        <div className="section-title review-title-row">
          <div><span className="label">错题复习</span><strong>用户错误类型</strong></div>
          <div className="segmented-control" aria-label="错误类型状态">
            {(['ACTIVE', 'ARCHIVED'] as const).map((item) => (
              <button key={item} type="button" className={status === item ? 'is-selected' : ''} onClick={() => handleStatusChange(item)}>
                {item === 'ACTIVE' ? '进行中' : '已归档'}
              </button>
            ))}
          </div>
        </div>

        {error ? <div className="notice is-error review-notice"><strong>加载失败</strong><p>{error}</p><button type="button" onClick={() => setReloadToken((value) => value + 1)}>重试</button></div> : null}
        {loading ? <p className="loading-text">正在加载错误类型...</p> : null}
        {!loading && !error && data.items.length === 0 ? <div className="empty-state">暂无{status === 'ACTIVE' ? '进行中' : '已归档'}的用户错误类型。</div> : null}

        {!loading && !error && data.items.length > 0 ? (
          <div className="table-scroll">
            <table className="user-error-type-table">
              <thead><tr><th>用户错误类型</th><th>说明</th><th>全局分类</th><th>状态</th><th>更新时间</th></tr></thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.id}>
                    <td>{item.name}</td><td>{item.description}</td><td>{item.errorTypeName}</td>
                    <td>{item.status === 'ACTIVE' ? '进行中' : '已归档'}</td><td>{formatDateTime(item.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        <div className="pagination-row">
          <span>共 {data.total} 条</span>
          <div className="pagination-actions">
            <button type="button" disabled={page <= 1 || loading} onClick={() => setPage((value) => value - 1)}>上一页</button>
            <span>{page} / {totalPages}</span>
            <button type="button" disabled={page >= totalPages || loading} onClick={() => setPage((value) => value + 1)}>下一页</button>
          </div>
        </div>
      </section>
    </section>
  )
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}
