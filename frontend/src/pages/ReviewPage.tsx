import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { getErrorMessage } from '../api/client'
import {
  fetchReviewCard,
  fetchReviewCards,
  generateDerivedReviewQuestion,
  submitReviewAttempt,
} from '../api/reviewApi'
import { confirmUserAnswerErrors, fetchUserErrorTypes } from '../api/userErrorApi'
import ErrorConfirmationModal from '../components/ErrorConfirmationModal'
import PageHeader from '../components/PageHeader'
import {
  type ErrorCandidateState,
  toErrorCandidateState,
  toExistingErrorConfirmation,
  toNewErrorConfirmation,
} from '../components/errorConfirmation'
import ReviewList from '../components/ReviewList'
import type { PageData, PracticeNotice } from '../types/api'
import type {
  ReviewAttemptHistory,
  ReviewAttemptResult,
  ReviewCard,
  ReviewCardDetail,
  ReviewCardFilterState,
  ReviewCardListMode,
  ReviewCycleProgress,
} from '../types/review'
import type { UserAnswerErrorConfirmation, UserErrorType } from '../types/userError'

const INITIAL_FILTERS: ReviewCardFilterState = { mode: 'DUE', page: 1, size: 20 }
const EMPTY_PAGE: PageData<ReviewCard> = { items: [], page: 1, size: 20, total: 0 }

type WorkbenchView = 'LIST' | 'CARD_DETAIL' | 'REVIEW' | 'RESULT'

export default function ReviewPage() {
  const [filters, setFilters] = useState<ReviewCardFilterState>(INITIAL_FILTERS)
  const [cards, setCards] = useState<PageData<ReviewCard>>(EMPTY_PAGE)
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)
  const [listReloadToken, setListReloadToken] = useState(0)
  const [view, setView] = useState<WorkbenchView>('LIST')
  const [selectedCardId, setSelectedCardId] = useState<number | null>(null)
  const [earlyReview, setEarlyReview] = useState(false)
  const [detail, setDetail] = useState<ReviewCardDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [detailReloadToken, setDetailReloadToken] = useState(0)
  const [answerText, setAnswerText] = useState('')
  const [submittedAnswer, setSubmittedAnswer] = useState('')
  const [submittedQuestionSource, setSubmittedQuestionSource] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [attemptResult, setAttemptResult] = useState<ReviewAttemptResult | null>(null)
  const [actionNotice, setActionNotice] = useState<PracticeNotice | null>(null)
  const [derivedGenerating, setDerivedGenerating] = useState(false)
  const [nextCardLoading, setNextCardLoading] = useState(false)
  const [errorCandidates, setErrorCandidates] = useState<ErrorCandidateState[]>([])
  const [errorConfirmationOpen, setErrorConfirmationOpen] = useState(false)
  const [errorConfirmationNotice, setErrorConfirmationNotice] = useState<PracticeNotice | null>(null)
  const [errorConfirming, setErrorConfirming] = useState(false)
  const [userErrorTypes, setUserErrorTypes] = useState<UserErrorType[]>([])
  const [userErrorTypesLoading, setUserErrorTypesLoading] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    setListLoading(true)
    setListError(null)
    fetchReviewCards(filters, controller.signal)
      .then(setCards)
      .catch((error: unknown) => {
        if (isAbortError(error)) return
        setCards({ ...EMPTY_PAGE, page: filters.page, size: filters.size })
        setListError(getErrorMessage(error))
      })
      .finally(() => {
        if (!controller.signal.aborted) setListLoading(false)
      })
    return () => controller.abort()
  }, [filters, listReloadToken])

  useEffect(() => {
    if (selectedCardId === null) return
    const controller = new AbortController()
    setDetailLoading(true)
    setDetailError(null)
    fetchReviewCard(selectedCardId, earlyReview, controller.signal)
      .then(setDetail)
      .catch((error: unknown) => {
        if (isAbortError(error)) return
        setDetailError(getErrorMessage(error))
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false)
      })
    return () => controller.abort()
  }, [detailReloadToken, earlyReview, selectedCardId])

  const totalPages = Math.max(1, Math.ceil(cards.total / filters.size))
  const firstItemNo = cards.total === 0 ? 0 : (filters.page - 1) * filters.size + 1
  const lastItemNo = Math.min(filters.page * filters.size, cards.total)
  const selectedErrorCount = useMemo(
    () => errorCandidates.filter((candidate) => candidate.selected && !candidate.saved).length,
    [errorCandidates],
  )

  function changeMode(mode: ReviewCardListMode) {
    setFilters((current) => ({ ...current, mode, page: 1 }))
  }

  function changePageSize(size: number) {
    setFilters((current) => ({ ...current, size, page: 1 }))
  }

  function openCard(
    cardId: number,
    nextView: Extract<WorkbenchView, 'CARD_DETAIL' | 'REVIEW'>,
    nextEarlyReview = false,
  ) {
    setSelectedCardId(cardId)
    setEarlyReview(nextEarlyReview)
    setDetail(null)
    setDetailError(null)
    setAnswerText('')
    setSubmittedAnswer('')
    setSubmittedQuestionSource('')
    setAttemptResult(null)
    setActionNotice(null)
    setErrorCandidates([])
    setErrorConfirmationOpen(false)
    setErrorConfirmationNotice(null)
    setView(nextView)
  }

  function startReview(cardId: number) {
    openCard(cardId, 'REVIEW')
  }

  function startEarlyReview(cardId: number) {
    openCard(cardId, 'REVIEW', true)
  }

  function viewCard(cardId: number) {
    openCard(cardId, 'CARD_DETAIL')
  }

  function startReviewFromDetail() {
    setDetail(null)
    setAnswerText('')
    setActionNotice(null)
    setView('REVIEW')
    setDetailReloadToken((value) => value + 1)
  }

  function startEarlyReviewFromDetail() {
    setDetail(null)
    setAnswerText('')
    setActionNotice(null)
    setEarlyReview(true)
    setView('REVIEW')
  }

  function returnToList() {
    if (submitting) return
    setView('LIST')
    setSelectedCardId(null)
    setEarlyReview(false)
    setDetail(null)
    setAttemptResult(null)
    setActionNotice(null)
    setListReloadToken((value) => value + 1)
  }

  function reloadDetail() {
    setActionNotice(null)
    setDetailReloadToken((value) => value + 1)
  }

  async function handleSubmitAttempt() {
    if (!detail?.currentQuestion || selectedCardId === null) return
    const normalizedAnswer = answerText.trim()
    if (!normalizedAnswer) {
      setActionNotice({ kind: 'error', title: '请填写答案', message: '输入日语答案后再提交。' })
      return
    }

    const questionSource = detail.currentQuestion.sourceText
    setSubmitting(true)
    setActionNotice(null)
    try {
      const result = await submitReviewAttempt(selectedCardId, {
        cycleQuestionId: detail.currentQuestion.cycleQuestionId,
        expectedAttemptCount: detail.currentQuestion.attemptCount,
        answerText: normalizedAnswer,
        earlyReview,
      })
      setSubmittedAnswer(normalizedAnswer)
      setSubmittedQuestionSource(questionSource)
      setAttemptResult(result)
      setErrorCandidates(result.errorAnalysis.map(toErrorCandidateState))
      setErrorConfirmationNotice(null)
      setErrorConfirmationOpen(false)
      setView('RESULT')
      setListReloadToken((value) => value + 1)
      setDetailReloadToken((value) => value + 1)
    } catch (error: unknown) {
      setActionNotice({ kind: 'error', title: '提交失败', message: getErrorMessage(error) })
    } finally {
      setSubmitting(false)
    }
  }

  async function handleGenerateDerivedQuestion() {
    if (selectedCardId === null) return
    setDerivedGenerating(true)
    setActionNotice(null)
    try {
      await generateDerivedReviewQuestion(selectedCardId)
      setAttemptResult((current) => current ? { ...current, derivedGenerationStatus: 'SUCCEEDED' } : current)
      setActionNotice({ kind: 'info', title: '衍生题已生成', message: '已刷新卡片，可以继续本周期复习。' })
      setView('REVIEW')
      setAnswerText('')
      setDetailReloadToken((value) => value + 1)
      setListReloadToken((value) => value + 1)
    } catch (error: unknown) {
      setActionNotice({ kind: 'error', title: '生成失败', message: getErrorMessage(error) })
    } finally {
      setDerivedGenerating(false)
    }
  }

  async function handleNextDueCard() {
    if (selectedCardId === null) return
    setNextCardLoading(true)
    setActionNotice(null)
    try {
      const dueCards = await fetchReviewCards({ mode: 'DUE', page: 1, size: 100 })
      const nextCard = dueCards.items.find((card) => card.id !== selectedCardId)
      if (!nextCard) {
        setActionNotice({ kind: 'info', title: '本轮已完成', message: '目前没有其他到期卡片。' })
        return
      }
      startReview(nextCard.id)
    } catch (error: unknown) {
      setActionNotice({ kind: 'error', title: '无法获取下一张', message: getErrorMessage(error) })
    } finally {
      setNextCardLoading(false)
    }
  }

  function continueEarlyReview() {
    if (selectedCardId === null) return
    setAnswerText('')
    setSubmittedAnswer('')
    setSubmittedQuestionSource('')
    setAttemptResult(null)
    setActionNotice(null)
    setErrorCandidates([])
    setEarlyReview(true)
    setView('REVIEW')
    setDetailReloadToken((value) => value + 1)
  }

  async function loadActiveUserErrorTypes() {
    setUserErrorTypesLoading(true)
    setErrorConfirmationNotice(null)
    try {
      const result = await fetchUserErrorTypes({ status: 'ACTIVE', page: 1, size: 100 })
      setUserErrorTypes(result.items)
    } catch (error: unknown) {
      setUserErrorTypes([])
      setErrorConfirmationNotice({ kind: 'error', title: '无法加载已有复习卡片', message: getErrorMessage(error) })
    } finally {
      setUserErrorTypesLoading(false)
    }
  }

  function openErrorConfirmation() {
    setErrorConfirmationNotice(null)
    setErrorConfirmationOpen(true)
    if (attemptResult?.errorAnalysis.length) void loadActiveUserErrorTypes()
  }

  function updateErrorCandidate(index: number, patch: Partial<ErrorCandidateState>) {
    setErrorCandidates((current) => current.map((candidate, candidateIndex) => (
      candidateIndex === index ? { ...candidate, ...patch } : candidate
    )))
    setErrorConfirmationNotice(null)
  }

  async function handleConfirmErrors() {
    if (!attemptResult) return false
    const selectedItems = attemptResult.errorAnalysis
      .map((analysis, index) => ({ analysis, candidate: errorCandidates[index], index }))
      .filter(({ candidate }) => candidate?.selected && !candidate.saved)
    if (selectedItems.length === 0) return false

    const payload: UserAnswerErrorConfirmation[] = []
    for (const { analysis, candidate, index } of selectedItems) {
      if (candidate.mode === 'NEW_USER_ERROR_TYPE') {
        if (!candidate.userErrorTypeName.trim() || !candidate.userErrorTypeDescription.trim()) {
          setErrorConfirmationNotice({ kind: 'error', title: '请补充复习卡片', message: '新建复习卡片需要名称和说明。' })
          return false
        }
        payload.push(toNewErrorConfirmation(analysis, candidate, index))
      } else {
        if (!candidate.userErrorTypeId) {
          setErrorConfirmationNotice({ kind: 'error', title: '请选择已有复习卡片', message: '添加记录前请选择对应的复习卡片。' })
          return false
        }
        payload.push(toExistingErrorConfirmation(analysis, candidate, index))
      }
    }

    setErrorConfirming(true)
    setErrorConfirmationNotice(null)
    try {
      await confirmUserAnswerErrors(attemptResult.userAnswerId, { errors: payload })
      const confirmedIndexes = new Set(selectedItems.map(({ index }) => index))
      setErrorCandidates((current) => current.map((candidate, index) => (
        confirmedIndexes.has(index) ? { ...candidate, selected: false, saved: true } : candidate
      )))
      setErrorConfirmationNotice({ kind: 'info', title: '复习卡片已更新', message: `已添加 ${selectedItems.length} 项复习内容。` })
      setListReloadToken((value) => value + 1)
      void loadActiveUserErrorTypes()
      return true
    } catch (error: unknown) {
      setErrorConfirmationNotice({ kind: 'error', title: '记录失败', message: getErrorMessage(error) })
      return false
    } finally {
      setErrorConfirming(false)
    }
  }

  if (view === 'CARD_DETAIL') {
    return <ReviewCardOverview
      detail={detail}
      loading={detailLoading}
      error={detailError}
      onStart={startReviewFromDetail}
      onStartEarly={startEarlyReviewFromDetail}
      onReload={reloadDetail}
      onBack={returnToList}
    />
  }

  if (view === 'REVIEW') {
    return <ReviewDetailView
      detail={detail}
      loading={detailLoading}
      error={detailError}
      answerText={answerText}
      submitting={submitting}
      derivedGenerating={derivedGenerating}
      earlyReview={earlyReview}
      notice={actionNotice}
      onAnswerChange={(value) => { setAnswerText(value); setActionNotice(null) }}
      onSubmit={handleSubmitAttempt}
      onGenerate={handleGenerateDerivedQuestion}
      onReload={reloadDetail}
      onBack={returnToList}
    />
  }

  if (view === 'RESULT' && attemptResult) {
    return <ReviewResultView
      card={detail}
      result={attemptResult}
      submittedAnswer={submittedAnswer}
      candidates={errorCandidates}
      notice={actionNotice}
      derivedGenerating={derivedGenerating}
      nextCardLoading={nextCardLoading}
      onGenerate={handleGenerateDerivedQuestion}
      onOpenErrorConfirmation={openErrorConfirmation}
      onContinueEarly={continueEarlyReview}
      onNext={handleNextDueCard}
      onBack={returnToList}
    >
      {errorConfirmationOpen ? <ErrorConfirmationModal
        analyses={attemptResult.errorAnalysis}
        candidates={errorCandidates}
        userErrorTypes={userErrorTypes}
        userErrorTypesLoading={userErrorTypesLoading}
        notice={errorConfirmationNotice}
        confirming={errorConfirming}
        selectedCount={selectedErrorCount}
        userAnswerId={attemptResult.userAnswerId}
        reviewCardSource={{ kind: 'FIXED', sourceText: submittedQuestionSource }}
        recommendedExpressions={attemptResult.standardAnswers.map((item) => item.answerText)}
        onUpdate={updateErrorCandidate}
        onConfirm={handleConfirmErrors}
        onCustomSaved={() => { setListReloadToken((value) => value + 1); void loadActiveUserErrorTypes() }}
        onClose={() => setErrorConfirmationOpen(false)}
      /> : null}
    </ReviewResultView>
  }

  return <section className="page-content target-page review-page" aria-label="复习卡片">
    <PageHeader
      eyebrow="复习卡片"
      title="复习卡片"
      description="按复习重点聚合的间隔复习计划，跟踪周期进度、待重试与下次到期时间。"
      actions={<div className="segmented-control review-list-modes" aria-label="复习卡片视图">
          {(['DUE', 'ACTIVE', 'MASTERED'] as const).map((mode) => (
            <button key={mode} type="button" className={filters.mode === mode ? 'is-selected' : ''} onClick={() => changeMode(mode)}>
              {listModeLabel(mode)}
            </button>
          ))}
        </div>}
    />
    <section className="surface review-surface review-workbench target-list-panel">

      {listError ? <Notice notice={{ kind: 'error', title: '加载失败', message: listError }} actionLabel="重试" onAction={() => setListReloadToken((value) => value + 1)} /> : null}
      {listLoading ? <p className="loading-text">正在加载复习卡片...</p> : null}
      {!listLoading && !listError && cards.items.length === 0 ? <div className="empty-state">{emptyListText(filters.mode)}</div> : null}

      {!listLoading && !listError && cards.items.length > 0 ? <div className="table-scroll">
        <table className="responsive-list-table review-card-table">
          <thead><tr><th>复习重点</th><th>全局分类</th><th>到期状态</th><th>周期进度</th><th>原题</th><th>待重试</th><th>最近活动</th><th>操作</th></tr></thead>
          <tbody>{cards.items.map((card) => {
            const due = card.status === 'ACTIVE' && isDue(card.dueAt)
            return <tr key={card.id}>
              <td className="table-ellipsis-cell" data-label="复习重点" title={card.userErrorTypeName}><strong>{card.userErrorTypeName}</strong></td>
              <td className="table-ellipsis-cell" data-label="全局分类" title={`${card.errorTypeName} (${card.errorTypeCode})`}>{card.errorTypeName}<span className="table-secondary-text">{card.errorTypeCode}</span></td>
              <td data-label="到期状态"><span className={`review-state-badge ${card.status === 'MASTERED' ? 'is-mastered' : due ? 'is-ready' : 'is-waiting'}`}>{card.status === 'MASTERED' ? '已掌握' : due ? '已到期' : '等待中'}</span><span className="table-secondary-text">{card.status === 'MASTERED' ? formatDateTime(card.masteredAt) : dueText(card.dueAt)}</span></td>
              <td data-label="周期进度"><ProgressBar progress={card.progress} /></td>
              <td data-label="原题">{card.progress.originalPassedCount} / {card.progress.originalQuestionCount}</td>
              <td data-label="待重试">{card.progress.retryQuestionCount}</td>
              <td data-label="最近活动">{formatDateTime(card.status === 'MASTERED' ? card.masteredAt : card.lastReviewedAt)}</td>
              <td data-label="操作"><div className="review-card-actions"><button type="button" className="compact-button" onClick={() => viewCard(card.id)}>查看</button>{due ? <button type="button" className="primary-button compact-button" onClick={() => startReview(card.id)}>开始复习</button> : filters.mode === 'ACTIVE' ? <button type="button" className="primary-button compact-button" onClick={() => startEarlyReview(card.id)}>提前复习</button> : null}</div></td>
            </tr>
          })}</tbody>
        </table>
      </div> : null}

      <div className="pagination-bar">
        <div className="pagination-summary"><span>{listLoading ? '加载中' : `第 ${filters.page} / ${totalPages} 页 · ${firstItemNo}-${lastItemNo} / ${cards.total}`}</span><label className="page-size-field"><span>每页数量</span><select value={filters.size} onChange={(event) => changePageSize(Number(event.target.value))}><option value={10}>10</option><option value={20}>20</option><option value={50}>50</option><option value={100}>100</option></select></label></div>
        <div className="pagination-actions"><button type="button" disabled={filters.page <= 1 || listLoading} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>上一页</button><button type="button" disabled={filters.page >= totalPages || listLoading} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>下一页</button><button type="button" disabled={listLoading} onClick={() => setListReloadToken((value) => value + 1)}>刷新</button></div>
      </div>
    </section>
  </section>
}

function ReviewCardOverview({ detail, loading, error, onStart, onStartEarly, onReload, onBack }: {
  detail: ReviewCardDetail | null
  loading: boolean
  error: string | null
  onStart: () => void
  onStartEarly: () => void
  onReload: () => void
  onBack: () => void
}) {
  return <section className="page-content target-page review-page" aria-label="复习卡片查看">
    <PageHeader eyebrow="复习卡片" title="复习卡片详情" actions={<><button type="button" onClick={onBack}>返回列表</button><button type="button" disabled={loading} onClick={onReload}>重新加载</button></>} />
    <section className="surface review-surface review-detail-surface">
      {loading && !detail ? <p className="loading-text">正在加载卡片详情...</p> : null}
      {error ? <Notice notice={{ kind: 'error', title: '卡片加载失败', message: error }} actionLabel="重新加载" onAction={onReload} /> : null}
      {detail ? <>
        <ReviewCardHeading detail={detail} showDescription />
        {detail.progress ? <ReviewMetrics progress={detail.progress} /> : null}
        <ReviewSchedule detail={detail} />
        {detail.reviewState === 'READY' ? <StateMessage title="卡片已到期" message="当前卡片已经可以复习。进入复习后才会显示题目和答案输入。" actionLabel="开始复习" onAction={onStart} /> : null}
        {detail.reviewState === 'WAITING' ? <StateMessage title="等待下次复习" message={`下次到期时间：${formatDateTime(detail.dueAt)}。也可以现在提前复习，计划将从本次实际作答日期重新计算。`} actionLabel="提前复习" onAction={onStartEarly} /> : null}
        {detail.reviewState === 'DERIVED_GENERATION_REQUIRED' ? <StateMessage title="需要继续本周期" message="本周期原题已经通过，但净成功尚未达到 4，需要生成衍生题继续复习。" actionLabel="继续复习" onAction={onStart} /> : null}
        {detail.reviewState === 'MASTERED' ? <StateMessage title="本周期已掌握" message={`完成时间：${formatDateTime(detail.masteredAt)}。再次在普通练习中记录同一复习重点时，会开启下一周期。`} /> : null}
        <ReviewAttemptHistoryList attempts={detail.reviewAttempts} />
      </> : null}
    </section>
  </section>
}

function ReviewDetailView({ detail, loading, error, answerText, submitting, derivedGenerating, earlyReview, notice, onAnswerChange, onSubmit, onGenerate, onReload, onBack }: {
  detail: ReviewCardDetail | null
  loading: boolean
  error: string | null
  answerText: string
  submitting: boolean
  derivedGenerating: boolean
  earlyReview: boolean
  notice: PracticeNotice | null
  onAnswerChange: (value: string) => void
  onSubmit: () => void
  onGenerate: () => void
  onReload: () => void
  onBack: () => void
}) {
  return <section className="page-content target-page review-page" aria-label="复习卡片详情">
    <PageHeader eyebrow="复习卡片" title={earlyReview ? '提前复习' : '复习卡片详情'} actions={<><button type="button" disabled={submitting} onClick={onBack}>返回列表</button><button type="button" disabled={loading || submitting || derivedGenerating} onClick={onReload}>重新加载</button></>} />
    <section className="surface review-surface review-detail-surface">
      {loading && !detail ? <p className="loading-text">正在加载卡片详情...</p> : null}
      {error ? <Notice notice={{ kind: 'error', title: '卡片加载失败', message: error }} actionLabel="重新加载" onAction={onReload} /> : null}
      {detail ? <>
        <ReviewCardHeading detail={detail} showDescription={false} />
        {detail.progress ? <ReviewMetrics progress={detail.progress} /> : null}
        <ReviewSchedule detail={detail} />
        {earlyReview ? <div className="notice"><strong>提前复习</strong><p>本次作答会正式更新周期进度，下次复习时间从今天开始计算。</p></div> : null}
        {notice ? <Notice notice={notice} /> : null}
        {detail.reviewState === 'READY' && detail.currentQuestion ? <div className="review-attempt-grid">
          <section className="review-question-block"><div className="section-title"><span className="label">{detail.currentQuestion.questionRole === 'DERIVED' ? '衍生题' : '原题'}</span><strong>请翻译为日语</strong></div><p className="review-question-source">{detail.currentQuestion.sourceText}</p>{detail.currentQuestion.contextText ? <p className="review-question-context">{detail.currentQuestion.contextText}</p> : null}<QuestionMetadata detail={detail} /></section>
          <section className="review-answer-block"><div className="section-title"><span className="label">作答</span><strong>输入日语答案</strong></div><textarea value={answerText} maxLength={2000} disabled={submitting} placeholder="请输入日语答案" onChange={(event) => onAnswerChange(event.target.value)} /><div className="action-row"><button type="button" className="primary-button" disabled={submitting || !answerText.trim()} onClick={onSubmit}>{submitting ? '评分中' : earlyReview ? '提交提前复习' : '提交答案'}</button><span className="answer-length">{answerText.length} / 2000</span></div></section>
        </div> : null}
        {detail.reviewState === 'WAITING' ? <StateMessage title="等待下次复习" message={`下次到期时间：${formatDateTime(detail.dueAt)}。到期前不能提交答案。`} /> : null}
        {detail.reviewState === 'DERIVED_GENERATION_REQUIRED' ? <StateMessage title="需要生成衍生题" message="本周期原题已通过，但净成功尚未达到 4，生成一道衍生题继续积累净成功。" actionLabel={derivedGenerating ? '生成中' : '生成衍生题'} actionDisabled={derivedGenerating} onAction={onGenerate} /> : null}
        {detail.reviewState === 'MASTERED' ? <StateMessage title="本周期已掌握" message={`完成时间：${formatDateTime(detail.masteredAt)}。再次在普通练习中记录同一复习重点时，会开启下一周期。`} /> : null}
      </> : null}
    </section>
  </section>
}

function ReviewCardHeading({ detail, showDescription }: { detail: ReviewCardDetail; showDescription: boolean }) {
  return <div className="review-card-heading"><div><span className="label">{detail.errorTypeName} · {detail.errorTypeCode}</span><h2>{detail.userErrorTypeName}</h2>{showDescription ? <p>{detail.userErrorTypeDescription || '暂无说明'}</p> : null}</div><span className={`review-state-badge ${reviewStateClass(detail.reviewState)}`}>{reviewStateLabel(detail.reviewState)}</span></div>
}

function ReviewSchedule({ detail }: { detail: ReviewCardDetail }) {
  return <dl className="review-sm2-grid"><div><dt>难度因子</dt><dd>{detail.easeFactor.toFixed(4)}</dd></div><div><dt>连续成功</dt><dd>{detail.repetitionCount}</dd></div><div><dt>当前间隔</dt><dd>{detail.intervalDays} 天</dd></div><div><dt>累计未通过次数</dt><dd>{detail.lapseCount}</dd></div><div><dt>最近复习</dt><dd>{formatDateTime(detail.lastReviewedAt)}</dd></div><div><dt>下次到期</dt><dd>{formatDateTime(detail.dueAt)}</dd></div></dl>
}

function ReviewAttemptHistoryList({ attempts }: { attempts: ReviewAttemptHistory[] }) {
  return <section className="review-attempt-history" aria-label="复习记录">
    <div className="section-title"><span className="label">历史</span><strong>复习记录</strong></div>
    {attempts.length === 0 ? <p className="empty-inline">暂无正式复习记录。</p> : <ol className="review-attempt-history-list">
      {attempts.map((attempt) => <li key={attempt.id}>
        <div className="review-attempt-history-header">
          <div className="review-attempt-history-meta"><time dateTime={attempt.createdAt}>{formatDateTime(attempt.createdAt)}</time><span>第 {attempt.cycleNo} 周期</span><span>{attempt.questionRole === 'DERIVED' ? '衍生题' : '原题'}</span></div>
          <div className="review-attempt-history-summary"><span className={`review-attempt-result ${attempt.result === 'PASS' ? 'is-pass' : 'is-fail'}`}>{attempt.result === 'PASS' ? '通过' : '失败'}</span><strong>总分 {formatReviewAttemptScore(attempt.totalScore)}</strong><span>质量 {attempt.quality} / 5</span></div>
        </div>
        <div className="review-attempt-history-content"><div><span>题目</span><p>{attempt.sourceText}</p><div className="review-attempt-reference"><span>参考答案</span><p>{attempt.referenceAnswer || '暂无参考答案。'}</p></div></div><div><span>你的答案</span><p>{attempt.answerText}</p></div></div>
      </li>)}
    </ol>}
  </section>
}

function ReviewResultView({ card, result, submittedAnswer, candidates, notice, derivedGenerating, nextCardLoading, onGenerate, onOpenErrorConfirmation, onContinueEarly, onNext, onBack, children }: {
  card: ReviewCardDetail | null
  result: ReviewAttemptResult
  submittedAnswer: string
  candidates: ErrorCandidateState[]
  notice: PracticeNotice | null
  derivedGenerating: boolean
  nextCardLoading: boolean
  onGenerate: () => void
  onOpenErrorConfirmation: () => void
  onContinueEarly: () => void
  onNext: () => void
  onBack: () => void
  children: ReactNode
}) {
  const passed = result.result === 'PASS'
  const savedCount = candidates.filter((candidate) => candidate.saved).length
  return <section className="page-content target-page review-page" aria-label="复习结果">
    <PageHeader eyebrow="复习卡片" title="复习结果" />
    <section className="surface review-surface review-result-surface">
      <div className={`review-result-banner ${passed ? 'is-pass' : 'is-fail'}`}><div><span className="label">本次评分</span><h2>{passed ? '回答通过' : '需要重试'}</h2><p>{result.feedback}</p></div><div className="review-score-summary"><div className="review-total-score"><span>总分</span><strong>{formatTotalScore(result.totalScore)}</strong><small>/ 100</small></div><div className="review-quality"><span>复习质量</span><strong>{result.quality}</strong><small>/ 5</small></div></div></div>
      {notice ? <Notice notice={notice} /> : null}
      <div className="review-result-grid"><section><span className="label">你的答案</span><p className="review-submitted-answer">{submittedAnswer}</p></section><section><span className="label">目标内容</span><strong>{result.targetErrorResolved ? '已掌握' : '待加强'}</strong><p>{result.targetErrorResolved ? '本次回答已经体现当前复习重点。' : '下一次仍会优先重试这道题。'}</p></section></div>
      <dl className="score-grid review-score-grid"><div><dt>语法与词汇</dt><dd>{result.scores.grammarVocabularyScore}</dd></div><div><dt>自然流畅度</dt><dd>{result.scores.naturalFluencyScore}</dd></div><div><dt>场景适配度</dt><dd>{result.scores.scenarioAdaptationScore}</dd></div><div><dt>信息完整性</dt><dd>{result.scores.informationCompletenessScore}</dd></div></dl>
      <ReviewMetrics progress={result.progress} />
      <section className="review-standard-answers"><div className="section-title"><span className="label">评分后公开</span><strong>标准与参考答案</strong></div>{result.standardAnswers.length > 0 ? <ol>{[...result.standardAnswers].sort((left, right) => left.sortOrder - right.sortOrder).map((answer) => <li key={answer.id}><span>{answer.primaryAnswer ? '标准' : '参考'}</span><p>{answer.answerText}</p></li>)}</ol> : <p className="empty-inline">暂无标准答案。</p>}</section>
      <ReviewList title="候选新错误" emptyText="本次未发现需要确认的新错误。" items={result.errorAnalysis}>{(item) => <div><span>{item.errorTypeName} / {item.severity}</span><strong>{item.original}</strong><p>{item.issue}</p><p>{item.suggestion}</p></div>}</ReviewList>
      <div className="error-record-action"><span>{result.errorAnalysis.length > 0 ? `${savedCount} / ${result.errorAnalysis.length} 项已加入复习卡片` : '可手动记录希望继续练习的表达'}</span><button type="button" className="primary-button" onClick={onOpenErrorConfirmation}>添加复习卡片</button></div>
      {result.derivedGenerationStatus === 'FAILED' ? <div className="review-generation-row"><div><strong>衍生题生成失败</strong><p>本次评分和复习进度已经保存，可单独重试生成。</p></div><button type="button" className="primary-button" disabled={derivedGenerating} onClick={onGenerate}>{derivedGenerating ? '生成中' : '重试生成'}</button></div> : null}
      {result.derivedGenerationStatus === 'SUCCEEDED' ? <div className="notice"><strong>衍生题已生成</strong><p>卡片已准备好后续复习题。</p></div> : null}
      <div className="review-result-actions">{result.cardStatus === 'ACTIVE' ? <button type="button" className="primary-button" disabled={nextCardLoading || derivedGenerating} onClick={onContinueEarly}>继续提前复习当前卡片</button> : null}<button type="button" className={result.cardStatus === 'MASTERED' ? 'primary-button' : undefined} disabled={nextCardLoading || derivedGenerating} onClick={onNext}>{nextCardLoading ? '加载中' : '复习下一张'}</button><button type="button" disabled={nextCardLoading || derivedGenerating} onClick={onBack}>返回列表</button><span>{result.cardStatus === 'MASTERED' ? '当前卡片已掌握' : `下次到期：${formatDateTime(result.nextDueAt)}`}</span></div>
      {card ? <p className="review-result-context">{card.userErrorTypeName} · 第 {result.progress.cycleNo} 周期</p> : null}
      {children}
    </section>
  </section>
}

function QuestionMetadata({ detail }: { detail: ReviewCardDetail }) {
  const question = detail.currentQuestion
  if (!question) return null
  const flags = [question.spoken ? '口语' : null, question.business ? '商务' : null, question.exam ? '考试' : null].filter(Boolean)
  return <div className="review-question-meta"><span>{question.level}</span><span>难度 {question.difficulty}</span>{flags.map((flag) => <span key={flag}>{flag}</span>)}{question.tags.map((tag) => <span key={tag.id}>{tag.name}</span>)}<span>已作答 {question.attemptCount} 次</span></div>
}

function ReviewMetrics({ progress }: { progress: ReviewCycleProgress }) {
  return <div className="review-metric-grid"><div><span>当前周期</span><strong>第 {progress.cycleNo} 轮</strong></div><div><span>净成功进度</span><strong>{progress.netSuccessCount} / {progress.targetSuccessCount}</strong><ProgressBar progress={progress} /><small>成功 {progress.successfulReviewCount} · 失败 {progress.failedReviewCount}</small></div><div><span>原题通过</span><strong>{progress.originalPassedCount} / {progress.originalQuestionCount}</strong></div><div><span>待处理</span><strong>{progress.retryQuestionCount + progress.pendingQuestionCount}</strong><small>{progress.retryQuestionCount} 道重试</small></div></div>
}

function ProgressBar({ progress }: { progress: ReviewCycleProgress }) {
  const percentage = progress.targetSuccessCount === 0 ? 0 : Math.max(0, Math.min(100, Math.round(progress.netSuccessCount / progress.targetSuccessCount * 100)))
  return <div className="review-progress" aria-label={`净成功进度 ${percentage}%`}><div className="review-progress-track"><span style={{ width: `${percentage}%` }} /></div><small>{progress.netSuccessCount} / {progress.targetSuccessCount}</small></div>
}

function StateMessage({ title, message, actionLabel, actionDisabled = false, onAction }: { title: string; message: string; actionLabel?: string; actionDisabled?: boolean; onAction?: () => void }) {
  return <div className="review-state-message"><strong>{title}</strong><p>{message}</p>{actionLabel && onAction ? <button type="button" className="primary-button" disabled={actionDisabled} onClick={onAction}>{actionLabel}</button> : null}</div>
}

function Notice({ notice, actionLabel, onAction }: { notice: PracticeNotice; actionLabel?: string; onAction?: () => void }) {
  return <div className={notice.kind === 'error' ? 'notice is-error review-notice' : 'notice review-notice'}><strong>{notice.title}</strong><p>{notice.message}</p>{actionLabel && onAction ? <button type="button" onClick={onAction}>{actionLabel}</button> : null}</div>
}

function listModeLabel(mode: ReviewCardListMode) {
  if (mode === 'DUE') return '待复习'
  if (mode === 'ACTIVE') return '全部进行中'
  return '已掌握'
}

function emptyListText(mode: ReviewCardListMode) {
  if (mode === 'DUE') return '当前没有到期卡片。'
  if (mode === 'ACTIVE') return '当前没有进行中的复习卡片。'
  return '目前还没有已掌握卡片。'
}

function reviewStateLabel(state: ReviewCardDetail['reviewState']) {
  if (state === 'READY') return '可复习'
  if (state === 'WAITING') return '等待到期'
  if (state === 'DERIVED_GENERATION_REQUIRED') return '待生成衍生题'
  return '已掌握'
}

function reviewStateClass(state: ReviewCardDetail['reviewState']) {
  if (state === 'READY') return 'is-ready'
  if (state === 'WAITING') return 'is-waiting'
  if (state === 'DERIVED_GENERATION_REQUIRED') return 'is-generation'
  return 'is-mastered'
}

function dueText(value: string | null) {
  if (!value) return '-'
  return isDue(value) ? `到期于 ${formatDateTime(value)}` : `下次 ${formatDateTime(value)}`
}

function isDue(value: string | null) {
  if (!value) return false
  const time = new Date(value).getTime()
  return Number.isFinite(time) && time <= Date.now()
}

function formatDateTime(value: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function formatTotalScore(value: number) {
  return value.toFixed(2)
}

function formatReviewAttemptScore(value: number | null) {
  return value === null ? '-' : `${value.toFixed(2)} / 100`
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}
