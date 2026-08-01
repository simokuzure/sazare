import { useEffect, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { fetchUserAnswers } from '../api/userAnswerApi'
import type { AnswerStatus, UserAnswerFilterState, UserAnswerRecord } from '../types/userAnswer'

const INITIAL_FILTERS: UserAnswerFilterState = {
  answerStatus: '',
  questionId: '',
  level: '',
  minTotalScore: '',
  maxTotalScore: '',
  page: 1,
  size: 20,
}

const STATUS_LABELS: Record<Exclude<AnswerStatus, ''>, string> = {
  SUBMITTED: '已提交',
  REVIEWED: '已评测',
  FAILED: '评测失败',
}

export default function AnswerRecordsPage() {
  const [records, setRecords] = useState<UserAnswerRecord[]>([])
  const [total, setTotal] = useState(0)
  const [filters, setFilters] = useState<UserAnswerFilterState>(INITIAL_FILTERS)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    async function loadRecords() {
      setLoading(true)
      setError(null)

      try {
        const result = await fetchUserAnswers(filters, controller.signal)
        setRecords(result.items)
        setTotal(result.total)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setRecords([])
        setTotal(0)
        setError(getErrorMessage(fetchError))
      } finally {
        setLoading(false)
      }
    }

    loadRecords()

    return () => {
      controller.abort()
    }
  }, [filters])

  const totalPages = Math.max(Math.ceil(total / filters.size), 1)
  const firstItemNo = total === 0 ? 0 : (filters.page - 1) * filters.size + 1
  const lastItemNo = Math.min(filters.page * filters.size, total)

  function updateFilters(patch: Partial<UserAnswerFilterState>) {
    setFilters((current) => ({
      ...current,
      ...patch,
      page: patch.page ?? 1,
    }))
  }

  function refreshRecords() {
    setFilters((current) => ({ ...current }))
  }

  return (
    <section className="page-content" aria-label="answer records page">
      <section className="surface answer-records-panel" aria-label="answer records query">
        <div className="section-title">
          <span className="label">学习历史</span>
          <strong>答题记录</strong>
        </div>

        <form className="answer-record-filter-bar" onSubmit={(event) => event.preventDefault()}>
          <label>
            <span>答题状态</span>
            <select
              value={filters.answerStatus}
              onChange={(event) => updateFilters({ answerStatus: event.target.value as AnswerStatus })}
            >
              <option value="">全部</option>
              <option value="SUBMITTED">已提交</option>
              <option value="REVIEWED">已评测</option>
              <option value="FAILED">评测失败</option>
            </select>
          </label>

          <label>
            <span>题目 ID</span>
            <input
              inputMode="numeric"
              pattern="[0-9]*"
              placeholder="不限制"
              value={filters.questionId}
              onChange={(event) => updateFilters({ questionId: event.target.value.replace(/\D/g, '') })}
            />
          </label>

          <label>
            <span>JLPT 等级</span>
            <select value={filters.level} onChange={(event) => updateFilters({ level: event.target.value })}>
              <option value="">全部</option>
              <option value="N5">N5</option>
              <option value="N4">N4</option>
              <option value="N3">N3</option>
              <option value="N2">N2</option>
              <option value="N1">N1</option>
            </select>
          </label>

          <label>
            <span>最低分</span>
            <input
              inputMode="decimal"
              placeholder="0"
              value={filters.minTotalScore}
              onChange={(event) => updateFilters({ minTotalScore: event.target.value })}
            />
          </label>

          <label>
            <span>最高分</span>
            <input
              inputMode="decimal"
              placeholder="100"
              value={filters.maxTotalScore}
              onChange={(event) => updateFilters({ maxTotalScore: event.target.value })}
            />
          </label>

          <label>
            <span>每页数量</span>
            <select value={filters.size} onChange={(event) => updateFilters({ size: Number(event.target.value) })}>
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </label>
        </form>

        {error ? (
          <div className="notice is-error">
            <strong>答题记录加载失败</strong>
            <p>{error}</p>
          </div>
        ) : null}

        <div className="table-wrap">
          <table className="answer-record-table">
            <thead>
              <tr>
                <th>记录</th>
                <th>题目</th>
                <th>中文原文</th>
                <th>等级/难度</th>
                <th>用户答案</th>
                <th>状态</th>
                <th>总分</th>
                <th>四项评分</th>
                <th>提交时间</th>
                <th>总体评价</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id}>
                  <td>#{record.id}</td>
                  <td>#{record.questionId}</td>
                  <td className="answer-record-source">{record.sourceText}</td>
                  <td>{formatLevelDifficulty(record)}</td>
                  <td className="answer-record-answer">{record.answerText}</td>
                  <td>{STATUS_LABELS[record.answerStatus]}</td>
                  <td>{formatScore(record.totalScore)}</td>
                  <td>{formatScores(record)}</td>
                  <td>{formatDateTime(record.createdAt)}</td>
                  <td className="answer-record-comment">{record.overallComment || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {!loading && records.length === 0 ? <p className="empty-state">暂无答题记录</p> : null}
        </div>

        <div className="pagination-bar">
          <span>
            {loading ? '加载中' : `第 ${filters.page} / ${totalPages} 页 · ${firstItemNo}-${lastItemNo} / ${total}`}
          </span>
          <div className="pagination-actions">
            <button
              type="button"
              disabled={filters.page <= 1 || loading}
              onClick={() => updateFilters({ page: filters.page - 1 })}
            >
              上一页
            </button>
            <button
              type="button"
              disabled={filters.page >= totalPages || loading}
              onClick={() => updateFilters({ page: filters.page + 1 })}
            >
              下一页
            </button>
            <button type="button" disabled={loading} onClick={refreshRecords}>
              刷新
            </button>
          </div>
        </div>
      </section>
    </section>
  )
}

function formatLevelDifficulty(record: UserAnswerRecord) {
  const level = record.level ?? '-'
  const difficulty = record.difficulty == null ? '-' : record.difficulty
  return `${level} / ${difficulty}`
}

function formatScore(score: number | null) {
  return score == null ? '-' : score.toFixed(2)
}

function formatScores(record: UserAnswerRecord) {
  const scores = record.scores
  return [
    scores.grammarVocabularyScore,
    scores.naturalFluencyScore,
    scores.scenarioAdaptationScore,
    scores.informationCompletenessScore,
  ]
    .map((score) => (score == null ? '-' : String(score)))
    .join(' / ')
}

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
