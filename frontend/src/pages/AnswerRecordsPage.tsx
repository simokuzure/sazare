import { useEffect, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { fetchUserAnswerDetail, fetchUserAnswers } from '../api/userAnswerApi'
import PageHeader from '../components/PageHeader'
import type { AnswerStatus, UserAnswerDetail, UserAnswerFilterState, UserAnswerRecord } from '../types/userAnswer'
import { useLanguage } from '../i18n/LanguageContext'

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
  const { english, learningMode, shortQuestionType, articleQuestionType, text } = useLanguage()
  const [records, setRecords] = useState<UserAnswerRecord[]>([])
  const [total, setTotal] = useState(0)
  const [filters, setFilters] = useState<UserAnswerFilterState>({ ...INITIAL_FILTERS, learningMode })
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
        title={text('答题记录', 'Answer history')}
        description={text('查看历次作答的评分结果，可按题型、状态、分数与等级筛选。', 'Review previous scores and filter by question type, status, score, or level.')}
      />
      <section className="surface answer-records-panel target-list-panel" aria-label="answer records query">
        <form className="answer-record-filter-bar" onSubmit={(event) => event.preventDefault()}>
          <label>
            <span>{text('题型', 'Question type')}</span>
            <select
              value={filters.questionType}
              onChange={(event) => updateFilters({ questionType: event.target.value as UserAnswerFilterState['questionType'] })}
            >
              <option value="">{text('全部', 'All')}</option>
              <option value={shortQuestionType}>{text('短句翻译', 'Sentence')}</option>
              <option value={articleQuestionType}>{text('文章翻译', 'Article')}</option>
              <option value="JAPANESE_CORRECTION">{text('日语纠错', 'Proofreading')}</option>
            </select>
          </label>
          <label>
            <span>{text('答题状态', 'Status')}</span>
            <select
              value={filters.answerStatus}
              onChange={(event) => updateFilters({ answerStatus: event.target.value as AnswerStatus })}
            >
              <option value="">{text('全部', 'All')}</option>
              <option value="SUBMITTED">{text('已提交', 'Submitted')}</option>
              <option value="REVIEWED">{text('已评测', 'Scored')}</option>
              <option value="FAILED">{text('评测失败', 'Failed')}</option>
            </select>
          </label>

          <label>
            <span>{text('题目 ID', 'Question ID')}</span>
            <input
              inputMode="numeric"
              pattern="[0-9]*"
              placeholder={text('不限制', 'Any')}
              value={filters.questionId}
              onChange={(event) => updateFilters({ questionId: event.target.value.replace(/\D/g, '') })}
            />
          </label>

          <label>
            <span>{text('JLPT 等级', 'JLPT')}</span>
            <select value={filters.level} onChange={(event) => updateFilters({ level: event.target.value })}>
              <option value="">{text('全部', 'All')}</option>
              <option value="N5">N5</option>
              <option value="N4">N4</option>
              <option value="N3">N3</option>
              <option value="N2">N2</option>
              <option value="N1">N1</option>
            </select>
          </label>

          <label>
            <span>{text('最低分', 'Min. score')}</span>
            <input
              inputMode="decimal"
              placeholder="0"
              value={filters.minTotalScore}
              onChange={(event) => updateFilters({ minTotalScore: event.target.value })}
            />
          </label>

          <label>
            <span>{text('最高分', 'Max. score')}</span>
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
            <strong>{text('答题记录加载失败', 'Could not load answer history')}</strong>
            <p>{error}</p>
          </div>
        ) : null}

        <div className="table-wrap">
          <table className="responsive-list-table answer-record-table">
            <thead>
              <tr>
                <th>{text('记录', 'ID')}</th>
                <th>{text('题目', 'Question')}</th>
                <th>{text('题型', 'Type')}</th>
                <th>{text('中文原文', 'Source')}</th>
                <th>{text('等级/难度', 'Level')}</th>
                <th>{text('用户答案', 'Answer')}</th>
                <th>{text('状态', 'Status')}</th>
                <th>{text('总分', 'Score')}</th>
                <th>{text('提交时间', 'Submitted')}</th>
                <th>{text('操作', 'Actions')}</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id}>
                  <td data-label={text('记录', 'ID')}>#{record.id}</td>
                  <td data-label={text('题目', 'Question')}>{record.questionId == null ? '-' : `#${record.questionId}`}</td>
                  <td data-label={text('题型', 'Type')}><span className="question-type-badge">{text(formatQuestionType(record.questionType), record.questionType == null ? 'Proofreading' : isArticleQuestion(record.questionType) ? 'Article' : 'Sentence')}</span></td>
                  <td className="table-question-answer-cell" data-label={text('中文原文', 'Source')} title={record.sourceText ?? undefined}>{record.sourceText ?? '-'}</td>
                  <td data-label={text('等级/难度', 'Level')}>{formatLevelDifficulty(record)}</td>
                  <td className="table-question-answer-cell" data-label={text('用户答案', 'Answer')} title={record.answerText}>{record.answerText}</td>
                  <td data-label={text('状态', 'Status')}><span className={record.answerStatus === 'REVIEWED' ? 'data-badge is-success' : record.answerStatus === 'FAILED' ? 'data-badge is-danger' : 'data-badge'}>{text(STATUS_LABELS[record.answerStatus], record.answerStatus === 'REVIEWED' ? 'Scored' : record.answerStatus === 'FAILED' ? 'Failed' : 'Submitted')}</span></td>
                  <td data-label={text('总分', 'Score')}>{formatReviewedScore(record.answerStatus, record.totalScore)}</td>
                  <td data-label={text('提交时间', 'Submitted')}>{formatDateTime(record.createdAt)}</td>
                  <td data-label={text('操作', 'Actions')}>
                    <div className="table-actions">
                      <button
                        type="button"
                        disabled={detailActionId === record.id}
                        onClick={() => handleSelectRecord(record.id)}
                      >
                        {text('查看', 'View')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {!loading && records.length === 0 ? <p className="empty-state">{text('暂无答题记录', 'No answer history')}</p> : null}
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
            <button type="button" disabled={loading} onClick={refreshRecords}>
              {text('刷新', 'Refresh')}
            </button>
          </div>
        </div>
      </section>
      </>
      ) : null}

      {viewMode === 'detail' ? (
        <section className="answer-records-panel target-detail-panel" aria-label="answer record detail">
          <PageHeader
            eyebrow={text('详情', 'Details')}
            title={detail ? text(`答题记录 #${detail.id}`, `Answer #${detail.id}`) : text('答题记录详情', 'Answer details')}
            actions={<button type="button" onClick={handleBackToList}>{text('返回列表', 'Back to history')}</button>}
          />

          {detailLoading ? (
            <div className="notice">
              <strong>{text('加载中', 'Loading')}</strong>
              <p>{text('正在加载答题记录详情。', 'Loading answer details.')}</p>
            </div>
          ) : null}

          {detailError ? (
            <div className="notice is-error">
              <strong>{text('答题记录详情加载失败', 'Could not load answer details')}</strong>
              <p>{detailError}</p>
            </div>
          ) : null}

          {detail ? (
            <>
              {detail.questionId == null ? (
                <dl className="question-details">
                  <div><dt>{text('题型', 'Type')}</dt><dd>{text('日语纠错', 'Proofreading')}</dd></div>
                  <div><dt>{text('提交时间', 'Submitted')}</dt><dd>{formatDateTime(detail.createdAt)}</dd></div>
                </dl>
              ) : <dl className="question-details">
                <div>
                  <dt>{text('题目', 'Question')}</dt>
                  <dd>#{detail.questionId}</dd>
                </div>
                <div>
                  <dt>{text('题型', 'Type')}</dt>
                  <dd>{text(formatQuestionType(detail.questionType), isArticleQuestion(detail.questionType) ? 'Article' : 'Sentence')}</dd>
                </div>
                <div>
                  <dt>{text('中文原文', 'Source')}</dt>
                  <dd>{isArticleQuestion(detail.questionType)
                    ? <ArticleSegments text={detail.sourceText ?? ''} />
                    : detail.sourceText ?? '-'}</dd>
                </div>
                <div>
                  <dt>{text('语境', 'Context')}</dt>
                  <dd>{detail.contextText ?? '-'}</dd>
                </div>
                <div>
                  <dt>{isArticleQuestion(detail.questionType) ? text('生词提示', 'Vocabulary hints') : text('语法点', 'Grammar point')}</dt>
                  <dd className={isArticleQuestion(detail.questionType) ? 'pre-wrap-text' : undefined}>{detail.grammarPoint ?? '-'}</dd>
                </div>
                <div>
                  <dt>{text('等级/难度', 'Level / difficulty')}</dt>
                  <dd>{formatLevelDifficulty(detail)}</dd>
                </div>
                <div>
                  <dt>{text('标签', 'Tags')}</dt>
                  <dd>
                    <span className="tag-chip-row">
                      {detail.tags.map((tag) => (
                        <span key={tag.id}>{english ? tag.nameEn : tag.name} / {tag.code}</span>
                      ))}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>{text('提交时间', 'Submitted')}</dt>
                  <dd>{formatDateTime(detail.createdAt)}</dd>
                </div>
              </dl>}

              <section className="submitted-answer pre-wrap-text">
                <span className="label">{detail.questionId == null ? text('日语原文', 'Your Japanese text') : isArticleQuestion(detail.questionType) ? text('用户完整译文', 'Your translation') : text('用户答案', 'Your answer')}</span>
                <p>{detail.answerText}</p>
              </section>

              {detail.questionId == null ? (
                <section className="article-revised-answer pre-wrap-text">
                  <strong>{text('完整纠正文稿', 'Revised text')}</strong>
                  <p>{detail.revisedText ?? '-'}</p>
                </section>
              ) : <section className="answer-reference">
                <strong>{text('标准/参考答案', 'Model answers')}</strong>
                <ol>
                  {detail.answers.map((answer) => (
                    <li key={answer.id}>
                      <span>{answer.answerType === 'STANDARD' ? text('标准', 'Standard') : text('参考', 'Alternative')}</span>
                      <strong>{isArticleQuestion(detail.questionType)
                        ? <ArticleSegments text={answer.answerText} />
                        : answer.answerText}</strong>
                    </li>
                  ))}
                </ol>
              </section>}

              <div className="score-summary">
                <span>{text('总分', 'Total score')}</span>
                <strong>{formatReviewedScore(detail.answerStatus, detail.totalScore)}</strong>
              </div>

              <dl className="score-grid">
                <div>
                  <dt>{detail.questionId == null ? text('语法与词汇准确性', 'Grammar & vocabulary') : isArticleQuestion(detail.questionType) ? text('语法与用词', 'Grammar & word choice') : text('语法与词汇', 'Grammar & vocabulary')}</dt>
                  <dd>{formatReviewedScore(detail.answerStatus, detail.scores.grammarVocabularyScore)}</dd>
                </div>
                <div>
                  <dt>{detail.questionId == null ? text('自然度与篇章连贯', 'Coherence') : isArticleQuestion(detail.questionType) ? text('自然度与篇章连贯', 'Coherence') : text('自然度与流畅度', 'Fluency')}</dt>
                  <dd>{formatReviewedScore(detail.answerStatus, detail.scores.naturalFluencyScore)}</dd>
                </div>
                <div>
                  <dt>{detail.questionId == null ? text('语体与风格一致性', 'Register & style') : isArticleQuestion(detail.questionType) ? text('体裁与语域', 'Genre & register') : text('敬语与场景', 'Context fit')}</dt>
                  <dd>{formatReviewedScore(detail.answerStatus, detail.scores.scenarioAdaptationScore)}</dd>
                </div>
                <div>
                  <dt>{detail.questionId == null ? text('表记与输入完整性', 'Writing completeness') : isArticleQuestion(detail.questionType) ? text('忠实度与完整性', 'Accuracy & completeness') : text('表达完整性', 'Completeness')}</dt>
                  <dd>{formatReviewedScore(detail.answerStatus, detail.scores.informationCompletenessScore)}</dd>
                </div>
              </dl>

              <section className="review-section">
                <strong>{text('总体评价', 'Overall feedback')}</strong>
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
  return isArticleQuestion(questionType) ? '文章' : '短句'
}

function isArticleQuestion(questionType: string | null | undefined) {
  return questionType?.endsWith('_ARTICLE') ?? false
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
