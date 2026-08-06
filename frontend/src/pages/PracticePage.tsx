import { useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { confirmUserAnswerErrors, fetchUserErrorTypes } from '../api/userErrorApi'
import { fetchRandomQuestion, generateQuestions, submitQuestionAnswer } from '../api/questionApi'
import { fetchTags } from '../api/tagApi'
import ErrorConfirmationModal from '../components/ErrorConfirmationModal'
import {
  type ErrorCandidateState,
  toErrorCandidateState,
  toExistingErrorConfirmation,
  toNewErrorConfirmation,
} from '../components/errorConfirmation'
import ReviewList from '../components/ReviewList'
import type { PracticeNotice } from '../types/api'
import type { Tag } from '../types/tag'
import type { AiQuestionGenerationPayload, Question, RandomQuestionFilter } from '../types/question'
import type { AnswerReview } from '../types/review'
import type { UserAnswerErrorConfirmation, UserErrorType } from '../types/userError'

type AnswerSessionState = {
  answerText: string
  answerSubmitted: boolean
  answerScoring: boolean
  answerReview: AnswerReview | null
  answerNotice: PracticeNotice | null
  errorCandidates: ErrorCandidateState[]
  errorConfirmationNotice: PracticeNotice | null
  errorConfirmationOpen: boolean
}

const EMPTY_ANSWER_SESSION: AnswerSessionState = {
  answerText: '',
  answerSubmitted: false,
  answerScoring: false,
  answerReview: null,
  answerNotice: null,
  errorCandidates: [],
  errorConfirmationNotice: null,
  errorConfirmationOpen: false,
}

export default function PracticePage() {
  const [practiceTags, setPracticeTags] = useState<Tag[]>([])
  const [practiceTagsLoading, setPracticeTagsLoading] = useState(false)
  const [practiceTagsError, setPracticeTagsError] = useState<string | null>(null)
  const [questionCount, setQuestionCount] = useState('1')
  const [level, setLevel] = useState('')
  const [difficulty, setDifficulty] = useState('3')
  const [sceneParentId, setSceneParentId] = useState('')
  const [sceneTagCode, setSceneTagCode] = useState('')
  const [extraRequirements, setExtraRequirements] = useState('')
  const [generatedQuestions, setGeneratedQuestions] = useState<Question[]>([])
  const [selectedQuestionIndex, setSelectedQuestionIndex] = useState(0)
  const [questionGenerating, setQuestionGenerating] = useState(false)
  const [questionRandomizing, setQuestionRandomizing] = useState(false)
  const [answerSessions, setAnswerSessions] = useState<Record<number, AnswerSessionState>>({})
  const [practiceNotice, setPracticeNotice] = useState<PracticeNotice | null>(null)
  const [userErrorTypes, setUserErrorTypes] = useState<UserErrorType[]>([])
  const [userErrorTypesLoading, setUserErrorTypesLoading] = useState(false)
  const [errorConfirming, setErrorConfirming] = useState(false)

  useEffect(() => {
    const controller = new AbortController()

    async function loadPracticeTags() {
      setPracticeTagsLoading(true)
      setPracticeTagsError(null)
      try {
        setPracticeTags(await fetchAllSceneTags(controller.signal))
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') return
        setPracticeTags([])
        setPracticeTagsError(getErrorMessage(fetchError))
      } finally {
        setPracticeTagsLoading(false)
      }
    }

    loadPracticeTags()
    return () => controller.abort()
  }, [])

  const sceneParentTags = useMemo(() => practiceTags.filter((tag) => tag.parentId === null), [practiceTags])
  const sceneChildTags = useMemo(() => practiceTags.filter((tag) => tag.parentId !== null), [practiceTags])
  const selectedSceneChildTags = useMemo(
    () => sceneParentId ? sceneChildTags.filter((tag) => tag.parentId === Number(sceneParentId)) : [],
    [sceneChildTags, sceneParentId],
  )
  const selectedQuestion = generatedQuestions[selectedQuestionIndex] ?? null
  const selectedQuestionId = selectedQuestion?.id ?? null
  const answerSession = selectedQuestionId ? answerSessions[selectedQuestionId] ?? EMPTY_ANSWER_SESSION : EMPTY_ANSWER_SESSION
  const {
    answerText,
    answerSubmitted,
    answerScoring,
    answerReview,
    answerNotice,
    errorCandidates,
    errorConfirmationNotice,
    errorConfirmationOpen,
  } = answerSession
  const answerInputNotice = answerNotice ?? practiceNotice
  const selectedErrorCount = errorCandidates.filter((candidate) => candidate.selected && !candidate.saved).length
  const questionLoading = questionGenerating || questionRandomizing

  async function handleGenerateQuestion() {
    setQuestionGenerating(true)
    setPracticeNotice(null)
    try {
      const questions = await generateQuestions(buildAiQuestionGenerationPayload())
      setGeneratedQuestions(questions)
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({
        kind: 'info',
        title: questions.length > 0 ? '题目已生成' : '未生成题目',
        message: questions.length > 0 ? `已生成 ${questions.length} 道题目。` : '请调整生成条件后重试。',
      })
    } catch (fetchError: unknown) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({ kind: 'error', title: '生成失败', message: getErrorMessage(fetchError) })
    } finally {
      setQuestionGenerating(false)
    }
  }

  async function handleRandomQuestion() {
    const filters = buildRandomQuestionFilter()
    if (sceneParentId && !sceneTagCode && filters.tagCodes.length === 0) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({ kind: 'info', title: '未找到题目', message: '当前一级场景下没有可用二级场景，请调整筛选条件。' })
      return
    }

    setQuestionRandomizing(true)
    setPracticeNotice(null)
    try {
      const question = await fetchRandomQuestion(filters)
      setGeneratedQuestions(question ? [question] : [])
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({
        kind: 'info',
        title: question ? '题目已抽取' : '未找到题目',
        message: question ? `已随机抽取题目 #${question.id}。` : '当前筛选条件下没有可用题目。',
      })
    } catch (fetchError: unknown) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({ kind: 'error', title: '抽题失败', message: getErrorMessage(fetchError) })
    } finally {
      setQuestionRandomizing(false)
    }
  }

  function handleSelectQuestion(index: number) {
    setSelectedQuestionIndex(index)
    setPracticeNotice(null)
  }

  function updateAnswerSession(questionId: number, updater: (session: AnswerSessionState) => AnswerSessionState) {
    setAnswerSessions((sessions) => ({
      ...sessions,
      [questionId]: updater(sessions[questionId] ?? EMPTY_ANSWER_SESSION),
    }))
  }

  function updateSelectedAnswerSession(updater: (session: AnswerSessionState) => AnswerSessionState) {
    if (selectedQuestionId === null) return
    updateAnswerSession(selectedQuestionId, updater)
  }

  async function handleSubmitAnswer() {
    if (!selectedQuestion) {
      setPracticeNotice({ kind: 'error', title: '请先选择题目', message: '生成题目后再提交作答。' })
      return
    }

    const submittedAnswer = answerText.trim()
    if (!submittedAnswer) {
      updateSelectedAnswerSession((session) => ({
        ...session,
        answerNotice: { kind: 'error', title: '请填写答案', message: '输入日语答案后再提交。' },
      }))
      return
    }

    const questionId = selectedQuestion.id
    updateAnswerSession(questionId, (session) => ({
      ...session,
      answerText: submittedAnswer,
      answerSubmitted: true,
      answerScoring: true,
      answerReview: null,
      answerNotice: null,
      errorCandidates: [],
      errorConfirmationNotice: null,
      errorConfirmationOpen: false,
    }))
    setPracticeNotice(null)

    try {
      const review = await submitQuestionAnswer(questionId, submittedAnswer)
      updateAnswerSession(questionId, (session) => ({
        ...session,
        answerReview: review,
        answerNotice: {
          kind: 'info',
          title: '评分完成',
          message: review ? `本次总分：${formatScore(review.totalScore)}` : '未获得评分结果。',
        },
        errorCandidates: review?.errorAnalysis.length ? review.errorAnalysis.map(toErrorCandidateState) : [],
      }))
    } catch (fetchError: unknown) {
      updateAnswerSession(questionId, (session) => ({
        ...session,
        answerNotice: { kind: 'error', title: '评分失败', message: getErrorMessage(fetchError) },
      }))
    } finally {
      updateAnswerSession(questionId, (session) => ({ ...session, answerScoring: false }))
    }
  }

  async function loadActiveUserErrorTypes() {
    setUserErrorTypesLoading(true)
    try {
      const result = await fetchUserErrorTypes({ status: 'ACTIVE', page: 1, size: 100 })
      setUserErrorTypes(result.items)
    } catch (fetchError: unknown) {
      setUserErrorTypes([])
      updateSelectedAnswerSession((session) => ({
        ...session,
        errorConfirmationNotice: { kind: 'error', title: '无法加载已有类型', message: getErrorMessage(fetchError) },
      }))
    } finally {
      setUserErrorTypesLoading(false)
    }
  }

  async function handleConfirmErrors() {
    if (!answerReview || selectedQuestionId === null) return
    const selectedItems = answerReview.errorAnalysis
      .map((analysis, index) => ({ analysis, candidate: errorCandidates[index], index }))
      .filter(({ candidate }) => candidate?.selected && !candidate.saved)

    if (selectedItems.length === 0) return

    const payload: UserAnswerErrorConfirmation[] = []
    for (const { analysis, candidate, index } of selectedItems) {
      if (candidate.mode === 'NEW_USER_ERROR_TYPE') {
        if (!candidate.userErrorTypeName.trim() || !candidate.userErrorTypeDescription.trim()) {
          updateSelectedAnswerSession((session) => ({
            ...session,
            errorConfirmationNotice: { kind: 'error', title: '请补充用户错误类型', message: '新建类型需要名称和说明。' },
          }))
          return
        }
        payload.push(toNewErrorConfirmation(analysis, candidate, index))
      } else {
        if (!candidate.userErrorTypeId) {
          updateSelectedAnswerSession((session) => ({
            ...session,
            errorConfirmationNotice: { kind: 'error', title: '请选择已有类型', message: '追加记录前请选择对应的用户错误类型。' },
          }))
          return
        }
        payload.push(toExistingErrorConfirmation(analysis, candidate, index))
      }
    }

    const questionId = selectedQuestionId
    setErrorConfirming(true)
    updateAnswerSession(questionId, (session) => ({ ...session, errorConfirmationNotice: null }))
    try {
      await confirmUserAnswerErrors(answerReview.userAnswerId, { errors: payload })
      const confirmedIndexes = new Set(selectedItems.map(({ index }) => index))
      updateAnswerSession(questionId, (session) => ({
        ...session,
        errorCandidates: session.errorCandidates.map((item, index) => (
          confirmedIndexes.has(index) ? { ...item, selected: false, saved: true } : item
        )),
        errorConfirmationNotice: { kind: 'info', title: '错误已记录', message: `已确认 ${selectedItems.length} 条错误。` },
        errorConfirmationOpen: false,
      }))
      void loadActiveUserErrorTypes()
    } catch (fetchError: unknown) {
      updateAnswerSession(questionId, (session) => ({
        ...session,
        errorConfirmationNotice: { kind: 'error', title: '记录失败', message: getErrorMessage(fetchError) },
      }))
    } finally {
      setErrorConfirming(false)
    }
  }

  function updateErrorCandidate(index: number, patch: Partial<ErrorCandidateState>) {
    updateSelectedAnswerSession((session) => ({
      ...session,
      errorCandidates: session.errorCandidates.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item),
      errorConfirmationNotice: null,
    }))
  }

  function handleOpenErrorConfirmation() {
    updateSelectedAnswerSession((session) => ({
      ...session,
      errorConfirmationNotice: null,
      errorConfirmationOpen: true,
    }))
    void loadActiveUserErrorTypes()
  }

  function handleClearAnswer() {
    updateSelectedAnswerSession(() => EMPTY_ANSWER_SESSION)
    setPracticeNotice(null)
  }

  function handleEditAnswer() {
    updateSelectedAnswerSession((session) => ({
      ...session,
      answerSubmitted: false,
      answerScoring: false,
      answerReview: null,
      answerNotice: null,
      errorCandidates: [],
      errorConfirmationNotice: null,
      errorConfirmationOpen: false,
    }))
    setPracticeNotice(null)
  }

  function buildAiQuestionGenerationPayload() {
    const payload: AiQuestionGenerationPayload = {}
    if (questionCount) payload.questionCount = Number(questionCount)
    if (level) payload.level = level
    if (difficulty) payload.difficulty = Number(difficulty)
    if (sceneTagCode) payload.sceneTagCodes = [sceneTagCode]
    if (generatedQuestions.length > 0) payload.excludedSourceTexts = generatedQuestions.map((question) => question.sourceText)
    if (extraRequirements.trim()) payload.extraRequirements = extraRequirements.trim()
    return payload
  }

  function buildRandomQuestionFilter(): RandomQuestionFilter {
    return {
      level,
      difficulty,
      tagCodes: buildRandomQuestionTagCodes(),
    }
  }

  function buildRandomQuestionTagCodes() {
    if (sceneTagCode) {
      return [sceneTagCode]
    }
    if (!sceneParentId) {
      return []
    }
    return selectedSceneChildTags.map((tag) => tag.code)
  }

  return (
    <section className="page-content" aria-label="练习">
      <div className="practice-grid">
        <section className="surface generator-panel" aria-label="题目生成">
          <form className="form-grid" onSubmit={(event) => event.preventDefault()}>
            <label><span>题目数量</span><select value={questionCount} onChange={(event) => setQuestionCount(event.target.value)}>{[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
            <label><span>JLPT 等级</span><select value={level} onChange={(event) => setLevel(event.target.value)}><option value="">默认 N3</option>{['N5', 'N4', 'N3', 'N2', 'N1'].map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
            <label><span>难度</span><select value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>{[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
            <label><span>场景一级</span><select value={sceneParentId} disabled={practiceTagsLoading} onChange={(event) => { setSceneParentId(event.target.value); setSceneTagCode('') }}><option value="">{practiceTagsLoading ? '加载中' : '不限场景'}</option>{sceneParentTags.map((tag) => <option key={tag.id} value={tag.id}>{tag.name}</option>)}</select></label>
            <label><span>场景二级</span><select value={sceneTagCode} disabled={practiceTagsLoading || !sceneParentId} onChange={(event) => setSceneTagCode(event.target.value)}><option value="">{practiceTagsLoading ? '加载中' : sceneParentId ? '不限场景' : '请先选择一级场景'}</option>{selectedSceneChildTags.map((tag) => <option key={tag.id} value={tag.code}>{tag.name}</option>)}</select></label>
            <label className="wide-field"><span>补充要求</span><input value={extraRequirements} maxLength={500} placeholder="例如：使用敬语、指定场景或语法点" onChange={(event) => setExtraRequirements(event.target.value)} /></label>
            <button type="button" disabled={questionLoading} onClick={handleRandomQuestion}>{questionRandomizing ? '抽题中' : '随机题目'}</button>
            <button type="button" className="primary-button" disabled={questionLoading} onClick={handleGenerateQuestion}>{questionGenerating ? '生成中' : '生成题目'}</button>
          </form>
          {practiceTagsError ? <div className="error-message">标签加载失败：{practiceTagsError}</div> : null}
        </section>

        <section className="surface question-panel" aria-label="题目预览">
          <div className="section-title"><span className="label">题目预览</span><strong>{selectedQuestion ? `题目 #${selectedQuestion.id}` : '尚未选择题目'}</strong></div>
          {generatedQuestions.length > 1 ? <div className="question-selector" aria-label="题目选择">{generatedQuestions.map((question, index) => <button key={question.id} type="button" className={selectedQuestionIndex === index ? 'is-selected' : ''} onClick={() => handleSelectQuestion(index)}>{index + 1}</button>)}</div> : null}
          <dl className="question-details">
            <div><dt>中文原文</dt><dd>{selectedQuestion?.sourceText ?? '暂无题目'}</dd></div>
            <div><dt>语境</dt><dd>{selectedQuestion?.contextText ?? '暂无'}</dd></div>
            <div><dt>语法点</dt><dd>{selectedQuestion?.grammarPoint ?? '暂无'}</dd></div>
            <div><dt>标签</dt><dd>{selectedQuestion ? <span className="tag-chip-row">{selectedQuestion.tags.map((tag) => <span key={tag.id}>{tag.name}</span>)}</span> : '暂无'}</dd></div>
            <div><dt>难度</dt><dd>{selectedQuestion ? `${selectedQuestion.level} / ${selectedQuestion.difficulty}` : '暂无'}</dd></div>
          </dl>
        </section>

        <section className="surface answer-panel" aria-label={answerSubmitted ? '评分结果' : '答案输入'}>
          <div className="section-title"><span className="label">{answerSubmitted ? '评分结果' : '作答'}</span><strong>{answerSubmitted ? '本次作答结果' : '输入日语答案'}</strong></div>
          {!answerSubmitted ? (
            <>
              {answerInputNotice && (answerInputNotice.kind === 'error' || !selectedQuestion) ? <Notice notice={answerInputNotice} /> : null}
              <textarea value={answerText} disabled={!selectedQuestion} placeholder={selectedQuestion ? '请输入日语答案' : '生成题目后即可作答'} onChange={(event) => updateSelectedAnswerSession((session) => ({ ...session, answerText: event.target.value }))} />
              <div className="action-row"><button type="button" className="primary-button" disabled={!selectedQuestion || answerScoring} onClick={handleSubmitAnswer}>{answerScoring ? '评分中' : '提交答案'}</button><button type="button" disabled={!selectedQuestion && !answerText} onClick={handleClearAnswer}>清空</button></div>
            </>
          ) : (
            <div className="answer-result">
              {answerNotice && (!answerReview || answerNotice.kind === 'error') ? <Notice notice={answerNotice} /> : null}
              {answerScoring ? <div className="notice"><strong>评分中</strong><p>正在分析本次作答。</p></div> : null}
              <section className="submitted-answer"><span className="label">你的答案</span><p>{answerText}</p></section>
              {answerReview ? <>
                <div className="score-summary"><span>总分</span><strong>{formatScore(answerReview.totalScore)}</strong></div>
                {errorConfirmationOpen ? (
                  <ErrorConfirmationModal
                    analyses={answerReview.errorAnalysis}
                    candidates={errorCandidates}
                    userErrorTypes={userErrorTypes}
                    userErrorTypesLoading={userErrorTypesLoading}
                    notice={errorConfirmationNotice}
                    confirming={errorConfirming}
                    selectedCount={selectedErrorCount}
                    onUpdate={updateErrorCandidate}
                    onConfirm={handleConfirmErrors}
                    onClose={() => updateSelectedAnswerSession((session) => ({ ...session, errorConfirmationOpen: false }))}
                  />
                ) : null}
                <details className="review-detail"><summary>详细评分说明</summary><div className="review-result">
                  <dl className="score-grid"><div><dt>语法与词汇</dt><dd>{answerReview.scores.grammarVocabularyScore}</dd></div><div><dt>自然流畅度</dt><dd>{answerReview.scores.naturalFluencyScore}</dd></div><div><dt>场景适配度</dt><dd>{answerReview.scores.scenarioAdaptationScore}</dd></div><div><dt>信息完整性</dt><dd>{answerReview.scores.informationCompletenessScore}</dd></div></dl>
                  <section className="review-section"><strong>总评</strong><p>{answerReview.overallComment}</p></section>
                  <dl className="comment-list"><div><dt>语法</dt><dd>{answerReview.comments.grammarComment}</dd></div><div><dt>词汇</dt><dd>{answerReview.comments.vocabularyComment}</dd></div><div><dt>自然度</dt><dd>{answerReview.comments.naturalnessComment}</dd></div><div><dt>场景</dt><dd>{answerReview.comments.scenarioComment}</dd></div></dl>
                  <ReviewList title="候选错误" emptyText="本次未发现明确错误。" items={answerReview.errorAnalysis}>{(item) => <div><span>{item.errorTypeName} / {item.severity}</span><strong>{item.original}</strong><p>{item.issue}</p><p>{item.suggestion}</p></div>}</ReviewList>
                  {answerReview.errorAnalysis.length > 0 ? (
                    <div className="error-record-action">
                      <span>{errorCandidates.filter((candidate) => candidate.saved).length} / {answerReview.errorAnalysis.length} 条错误已记录</span>
                      <button
                        type="button"
                        className="primary-button"
                        disabled={errorCandidates.every((candidate) => candidate.saved)}
                        onClick={handleOpenErrorConfirmation}
                      >
                        记录错误
                      </button>
                    </div>
                  ) : null}
                  <ReviewList title="修改建议" emptyText="本次没有额外修改建议。" items={answerReview.revisionSuggestions}>{(item) => <p>{item}</p>}</ReviewList>
                  <ReviewList title="推荐表达" emptyText="本次没有推荐表达。" items={answerReview.recommendedExpressions}>{(item) => <div><span>{item.formality}</span><strong>{item.expression}</strong><p>{item.usage}</p><p>{item.note}</p></div>}</ReviewList>
                </div></details>
              </> : null}
              <div className="action-row"><button type="button" className="primary-button" disabled={answerScoring || errorConfirming} onClick={handleEditAnswer}>修改答案</button><button type="button" disabled={answerScoring || errorConfirming} onClick={handleClearAnswer}>清空</button></div>
            </div>
          )}
        </section>
      </div>
    </section>
  )
}

async function fetchAllSceneTags(signal: AbortSignal): Promise<Tag[]> {
  const pageSize = 100
  const firstPage = await fetchTags({ tagType: 'SCENE', enabledOnly: true, page: 1, size: pageSize }, signal)
  const totalPages = Math.ceil(firstPage.total / pageSize)
  if (totalPages <= 1) return firstPage.items

  const remainingPages = await Promise.all(
    Array.from({ length: totalPages - 1 }, (_, index) => fetchTags({
      tagType: 'SCENE',
      enabledOnly: true,
      page: index + 2,
      size: pageSize,
    }, signal)),
  )
  return [firstPage, ...remainingPages].flatMap((page) => page.items)
}

function Notice({ notice }: { notice: PracticeNotice }) {
  return <div className={notice.kind === 'error' ? 'notice is-error' : 'notice'}><strong>{notice.title}</strong><p>{notice.message}</p></div>
}

function formatScore(score: number) {
  return score.toFixed(2)
}
