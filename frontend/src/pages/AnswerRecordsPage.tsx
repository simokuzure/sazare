import { useEffect, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { fetchUserAnswerDetail, fetchUserAnswers } from '../api/userAnswerApi'
import PageHeader from '../components/PageHeader'
import type { AnswerStatus, UserAnswerDetail, UserAnswerFilterState, UserAnswerRecord } from '../types/userAnswer'

type AnswerRecordViewMode = 'list' | 'detail'

const INITIAL_FILTERS: UserAnswerFilterState = {
  answerStatus: '',
  questionType: '',
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
  const [viewMode, setViewMode] = useState<AnswerRecordViewMode>('list')
  const [detail, setDetail] = useState<UserAnswerDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [detailActionId, setDetailActionId] = useState<number | null>(null)

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

  async function handleSelectRecord(recordId: number) {
    setViewMode('detail')
    setDetail(null)
    setDetailError(null)
    setDetailLoading(true)
    setDetailActionId(recordId)

    try {
      const result = await fetchUserAnswerDetail(recordId)
      setDetail(result)
    } catch (fetchError: unknown) {
      setDetailError(getErrorMessage(fetchError))
    } finally {
      setDetailLoading(false)
      setDetailActionId(null)
    }
  }

  function handleBackToList() {
    setViewMode('list')
    setDetailError(null)
  }

  return (
    <section className="page-content target-page answer-records-page" aria-label="answer records page">
      {viewMode === 'list' ? (
      <>
      <PageHeader
        title="答题记录"
        description="查看历次作答的评分结果，可按题型、状态、分数与等级筛选。"
      />
      <section className="surface answer-records-panel target-list-panel" aria-label="answer records query">
        <form className="answer-record-filter-bar" onSubmit={(event) => event.preventDefault()}>
          <label>
            <span>题型</span>
            <select
              value={filters.questionType}
              onChange={(event) => updateFilters({ questionType: event.target.value as UserAnswerFilterState['questionType'] })}
            >
              <option value="">全部</option>
              <option value="TRANSLATION_ZH_TO_JA">短句翻译</option>
              <option value="TRANSLATION_ZH_TO_JA_ARTICLE">文章翻译</option>
              <option value="JAPANESE_CORRECTION">日语纠错</option>
            </select>
          </label>
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

        </form>

        {error ? (
          <div className="notice is-error">
            <strong>答题记录加载失败</strong>
            <p>{error}</p>
          </div>
        ) : null}

        <div className="table-wrap">
          <table className="responsive-list-table answer-record-table">
            <thead>
              <tr>
                <th>记录</th>
                <th>题目</th>
                <th>题型</th>
                <th>中文原文</th>
                <th>等级/难度</th>
                <th>用户答案</th>
                <th>状态</th>
                <th>总分</th>
                <th>提交时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id}>
                  <td data-label="记录">#{record.id}</td>
                  <td data-label="题目">{record.questionId == null ? '-' : `#${record.questionId}`}</td>
                  <td data-label="题型"><span className="question-type-badge">{formatQuestionType(record.questionType)}</span></td>
                  <td className="table-question-answer-cell" data-label="中文原文" title={record.sourceText ?? undefined}>{record.sourceText ?? '-'}</td>
                  <td data-label="等级/难度">{formatLevelDifficulty(record)}</td>
                  <td className="table-question-answer-cell" data-label="用户答案" title={record.answerText}>{record.answerText}</td>
                  <td data-label="状态"><span className={record.answerStatus === 'REVIEWED' ? 'data-badge is-success' : record.answerStatus === 'FAILED' ? 'data-badge is-danger' : 'data-badge'}>{STATUS_LABELS[record.answerStatus]}</span></td>
                  <td data-label="总分">{formatReviewedScore(record.answerStatus, record.totalScore)}</td>
                  <td data-label="提交时间">{formatDateTime(record.createdAt)}</td>
                  <td data-label="操作">
                    <div className="table-actions">
                      <button
                        type="button"
                        disabled={detailActionId === record.id}
                        onClick={() => handleSelectRecord(record.id)}
                      >
                        查看
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {!loading && records.length === 0 ? <p className="empty-state">暂无答题记录</p> : null}
        </div>

        <div className="pagination-bar">
          <div className="pagination-summary">
            <span>
              {loading ? '加载中' : `第 ${filters.page} / ${totalPages} 页 · ${firstItemNo}-${lastItemNo} / ${total}`}
            </span>
            <label className="page-size-field">
              <span>每页数量</span>
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
      </>
      ) : null}

      {viewMode === 'detail' ? (
        <section className="answer-records-panel target-detail-panel" aria-label="answer record detail">
          <PageHeader
            eyebrow="详情"
            title={detail ? `答题记录 #${detail.id}` : '答题记录详情'}
            actions={<button type="button" onClick={handleBackToList}>返回列表</button>}
          />

          {detailLoading ? (
            <div className="notice">
              <strong>加载中</strong>
              <p>正在加载答题记录详情。</p>
            </div>
          ) : null}

          {detailError ? (
            <div className="notice is-error">
              <strong>答题记录详情加载失败</strong>
              <p>{detailError}</p>
            </div>
          ) : null}

          {detail ? (
            <>
              {detail.questionId == null ? (
                <dl className="question-details">
                  <div><dt>题型</dt><dd>日语纠错</dd></div>
                  <div><dt>提交时间</dt><dd>{formatDateTime(detail.createdAt)}</dd></div>
                </dl>
              ) : <dl className="question-details">
                <div>
                  <dt>题目</dt>
                  <dd>#{detail.questionId}</dd>
                </div>
                <div>
                  <dt>题型</dt>
                  <dd>{formatQuestionType(detail.questionType)}</dd>
                </div>
                <div>
                  <dt>中文原文</dt>
                  <dd>{detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE'
                    ? <ArticleSegments text={detail.sourceText ?? ''} />
                    : detail.sourceText ?? '-'}</dd>
                </div>
                <div>
                  <dt>语境</dt>
                  <dd>{detail.contextText ?? '-'}</dd>
                </div>
                <div>
                  <dt>{detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '生词提示' : '语法点'}</dt>
                  <dd className={detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? 'pre-wrap-text' : undefined}>{detail.grammarPoint ?? '-'}</dd>
                </div>
                <div>
                  <dt>等级/难度</dt>
                  <dd>{formatLevelDifficulty(detail)}</dd>
                </div>
                <div>
                  <dt>标签</dt>
                  <dd>
                    <span className="tag-chip-row">
                      {detail.tags.map((tag) => (
                        <span key={tag.id}>{tag.name} / {tag.code}</span>
                      ))}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>提交时间</dt>
                  <dd>{formatDateTime(detail.createdAt)}</dd>
                </div>
              </dl>}

              <section className="submitted-answer pre-wrap-text">
                <span className="label">{detail.questionId == null ? '日语原文' : detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '用户完整译文' : '用户答案'}</span>
                <p>{detail.answerText}</p>
              </section>

              {detail.questionId == null ? (
                <section className="article-revised-answer pre-wrap-text">
                  <strong>完整纠正文稿</strong>
                  <p>{detail.revisedText ?? '-'}</p>
                </section>
              ) : <section className="answer-reference">
                <strong>标准/参考答案</strong>
                <ol>
                  {detail.answers.map((answer) => (
                    <li key={answer.id}>
                      <span>{answer.answerType === 'STANDARD' ? '标准' : '参考'}</span>
                      <strong>{detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE'
                        ? <ArticleSegments text={answer.answerText} />
                        : answer.answerText}</strong>
                    </li>
                  ))}
                </ol>
              </section>}

              <div className="score-summary">
                <span>总分</span>
                <strong>{formatReviewedScore(detail.answerStatus, detail.totalScore)}</strong>
              </div>

              <dl className="score-grid">
                <div>
                  <dt>{detail.questionId == null ? '语法与词汇准确性' : detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '语法与用词' : '语法与词汇'}</dt>
                  <dd>{formatReviewedScore(detail.answerStatus, detail.scores.grammarVocabularyScore)}</dd>
                </div>
                <div>
                  <dt>{detail.questionId == null ? '自然度与篇章连贯' : detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '自然度与篇章连贯' : '自然度与流畅度'}</dt>
                  <dd>{formatReviewedScore(detail.answerStatus, detail.scores.naturalFluencyScore)}</dd>
                </div>
                <div>
                  <dt>{detail.questionId == null ? '语体与风格一致性' : detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '体裁与语域' : '敬语与场景'}</dt>
                  <dd>{formatReviewedScore(detail.answerStatus, detail.scores.scenarioAdaptationScore)}</dd>
                </div>
                <div>
                  <dt>{detail.questionId == null ? '表记与输入完整性' : detail.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '忠实度与完整性' : '表达完整性'}</dt>
                  <dd>{formatReviewedScore(detail.answerStatus, detail.scores.informationCompletenessScore)}</dd>
                </div>
              </dl>

              <section className="review-section">
                <strong>总体评价</strong>
                <p>{detail.answerStatus === 'REVIEWED' ? detail.overallComment || '-' : '-'}</p>
              </section>
            </>
          ) : null}
        </section>
      ) : null}
    </section>
  )
}

function formatLevelDifficulty(record: Pick<UserAnswerRecord, 'level' | 'difficulty'>) {
  const level = record.level ?? '-'
  const difficulty = record.difficulty == null ? '-' : record.difficulty
  return `${level} / ${difficulty}`
}

function formatScore(score: number | null | undefined) {
  return score == null ? '-' : score.toFixed(2)
}

function formatReviewedScore(status: Exclude<AnswerStatus, ''>, score: number | null | undefined) {
  return status === 'REVIEWED' ? formatScore(score) : '-'
}

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function formatQuestionType(questionType: UserAnswerRecord['questionType']) {
  if (questionType == null) return '日语纠错'
  return questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '文章' : '短句'
}

function splitArticleSegments(text: string) {
  const normalized = text.replace(/\r\n?/g, '\n').trim()
  if (!normalized) return []
  return normalized.split(/\n\s*\n/).map((segment) => segment.trim()).filter(Boolean)
}

function ArticleSegments({ text }: { text: string }) {
  return (
    <ol className="article-segment-list compact">
      {splitArticleSegments(text).map((segment, index) => <li key={`${index}-${segment}`}>{segment}</li>)}
    </ol>
  )
}
