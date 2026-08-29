import { useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { confirmUserAnswerErrors, fetchUserErrorTypes } from '../api/userErrorApi'
import { fetchRandomQuestion, generateQuestions, submitQuestionAnswer } from '../api/questionApi'
import { fetchAllTags } from '../api/tagApi'
import ErrorConfirmationModal from '../components/ErrorConfirmationModal'
import PageHeader from '../components/PageHeader'
import TagCascadeSelect from '../components/TagCascadeSelect'
import {
  type ErrorCandidateState,
  toErrorCandidateState,
  toExistingErrorConfirmation,
  toNewErrorConfirmation,
} from '../components/errorConfirmation'
import ReviewList from '../components/ReviewList'
import ArticlePractice from './ArticlePractice'
import JapaneseCorrectionPractice from './JapaneseCorrectionPractice'
import type { PracticeNotice } from '../types/api'
import type { Tag } from '../types/tag'
import type { AiQuestionGenerationPayload, Question, RandomQuestionFilter } from '../types/question'
import type { AnswerReview } from '../types/review'
import type { UserAnswerErrorConfirmation, UserErrorType } from '../types/userError'
import { useLanguage } from '../i18n/LanguageContext'
import { scoreToneClassName } from '../utils/score'
import { getTagDisplayName } from '../utils/tag'

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

type PracticeMode = 'sentence' | 'article' | 'correction'

const PRACTICE_MODES: PracticeMode[] = ['sentence', 'article', 'correction']

export default function PracticePage() {
  const { text } = useLanguage()
  const [activeMode, setActiveMode] = useState<PracticeMode>('sentence')

  function handleTabKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, currentMode: PracticeMode) {
    const currentIndex = PRACTICE_MODES.indexOf(currentMode)
    let nextIndex = currentIndex
    if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % PRACTICE_MODES.length
    else if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + PRACTICE_MODES.length) % PRACTICE_MODES.length
    else if (event.key === 'Home') nextIndex = 0
    else if (event.key === 'End') nextIndex = PRACTICE_MODES.length - 1
    else return

    event.preventDefault()
    const nextMode = PRACTICE_MODES[nextIndex]
    setActiveMode(nextMode)
    requestAnimationFrame(() => document.getElementById(`${nextMode}-practice-tab`)?.focus())
  }

  return (
    <section className="page-content" aria-label={text('练习', 'Practice')}>
      <PageHeader
        title={text('翻译练习', 'Translation practice')}
        description={text('选择练习模式，生成题目并提交作答，AI 会即时评分与纠错。', 'Choose a practice type, generate a question, and receive immediate AI feedback.')}
        actions={<div className="practice-tabs" role="tablist" aria-label={text('翻译练习类型', 'Practice type')}>
        <button
          id="sentence-practice-tab"
          type="button"
          role="tab"
          aria-selected={activeMode === 'sentence'}
          aria-controls="sentence-practice-panel"
          className={activeMode === 'sentence' ? 'is-active' : ''}
          tabIndex={activeMode === 'sentence' ? 0 : -1}
          onKeyDown={(event) => handleTabKeyDown(event, 'sentence')}
          onClick={() => setActiveMode('sentence')}
        >
          {text('短句翻译', 'Sentences')}
        </button>
        <button
          id="article-practice-tab"
          type="button"
          role="tab"
          aria-selected={activeMode === 'article'}
          aria-controls="article-practice-panel"
          className={activeMode === 'article' ? 'is-active' : ''}
          tabIndex={activeMode === 'article' ? 0 : -1}
          onKeyDown={(event) => handleTabKeyDown(event, 'article')}
          onClick={() => setActiveMode('article')}
        >
          {text('文章翻译', 'Articles')}
        </button>
        <button
          id="correction-practice-tab"
          type="button"
          role="tab"
          aria-selected={activeMode === 'correction'}
          aria-controls="correction-practice-panel"
          className={activeMode === 'correction' ? 'is-active' : ''}
          tabIndex={activeMode === 'correction' ? 0 : -1}
          onKeyDown={(event) => handleTabKeyDown(event, 'correction')}
          onClick={() => setActiveMode('correction')}
        >
          {text('日语纠错', 'Proofread')}
        </button>
      </div>}
      />

      <div
        id="sentence-practice-panel"
        role="tabpanel"
        aria-labelledby="sentence-practice-tab"
        hidden={activeMode !== 'sentence'}
      >
        <ShortSentencePractice />
      </div>
      <div
        id="article-practice-panel"
        role="tabpanel"
        aria-labelledby="article-practice-tab"
        hidden={activeMode !== 'article'}
      >
        <ArticlePractice />
      </div>
      <div
        id="correction-practice-panel"
        role="tabpanel"
        aria-labelledby="correction-practice-tab"
        hidden={activeMode !== 'correction'}
      >
        <JapaneseCorrectionPractice />
      </div>
    </section>
  )
}

function ShortSentencePractice() {
  const { english, learningMode, shortQuestionType, text } = useLanguage()
  const [practiceTags, setPracticeTags] = useState<Tag[]>([])
  const [practiceTagsLoading, setPracticeTagsLoading] = useState(false)
  const [practiceTagsError, setPracticeTagsError] = useState<string | null>(null)
  const [questionCount, setQuestionCount] = useState('1')
  const [level, setLevel] = useState('N3')
  const [difficulty, setDifficulty] = useState('3')
  const [sceneParentId, setSceneParentId] = useState('')
  const [sceneTagCode, setSceneTagCode] = useState('')
  const [extraRequirements, setExtraRequirements] = useState('')
  const [generatedQuestions, setGeneratedQuestions] = useState<Question[]>([])
  const [selectedQuestionIndex, setSelectedQuestionIndex] = useState(0)
  const [grammarPointVisible, setGrammarPointVisible] = useState(false)
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
        setPracticeTags(await fetchAllTags({ tagType: 'SCENE', enabledOnly: true }, controller.signal))
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
    setGrammarPointVisible(false)
    setPracticeNotice(null)
    try {
      const questions = await generateQuestions(buildAiQuestionGenerationPayload())
      setGeneratedQuestions(questions)
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({
        kind: 'info',
        title: questions.length > 0 ? text('题目已生成', 'Questions generated') : text('未生成题目', 'No questions generated'),
        message: questions.length > 0 ? text(`已生成 ${questions.length} 道题目。`, `Generated ${questions.length} question(s).`) : text('请调整生成条件后重试。', 'Adjust the generation settings and try again.'),
      })
    } catch (fetchError: unknown) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({ kind: 'error', title: text('生成失败', 'Generation failed'), message: getErrorMessage(fetchError) })
    } finally {
      setQuestionGenerating(false)
    }
  }

  async function handleRandomQuestion() {
    const filters = buildRandomQuestionFilter()
    setGrammarPointVisible(false)
    if (sceneParentId && !sceneTagCode && filters.tagCodes.length === 0) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({ kind: 'info', title: text('未找到题目', 'No question found'), message: text('当前一级场景下没有可用二级场景，请调整筛选条件。', 'The selected primary scene has no available secondary scenes. Adjust the filters.') })
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
        title: question ? text('题目已抽取', 'Question selected') : text('未找到题目', 'No question found'),
        message: question ? text(`已随机抽取题目 #${question.id}。`, `Randomly selected question #${question.id}.`) : text('当前筛选条件下没有可用题目。', 'No questions match the current filters.'),
      })
    } catch (fetchError: unknown) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setAnswerSessions({})
      setPracticeNotice({ kind: 'error', title: text('抽题失败', 'Could not select a question'), message: getErrorMessage(fetchError) })
    } finally {
      setQuestionRandomizing(false)
    }
  }

  function handleSelectQuestion(index: number) {
    setSelectedQuestionIndex(index)
    setGrammarPointVisible(false)
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
      setPracticeNotice({ kind: 'error', title: text('请先选择题目', 'Select a question first'), message: text('生成题目后再提交作答。', 'Generate a question before submitting an answer.') })
      return
    }

    const submittedAnswer = answerText.trim()
    if (!submittedAnswer) {
      updateSelectedAnswerSession((session) => ({
        ...session,
        answerNotice: { kind: 'error', title: text('请填写答案', 'Enter an answer'), message: text('输入日语答案后再提交。', 'Enter your Japanese answer before submitting.') },
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
          title: text('评分完成', 'Scoring complete'),
          message: review ? text(`本次总分：${formatScore(review.totalScore)}`, `Total score: ${formatScore(review.totalScore)}`) : text('未获得评分结果。', 'No score was returned.'),
        },
        errorCandidates: review?.errorAnalysis.length ? review.errorAnalysis.map(toErrorCandidateState) : [],
      }))
    } catch (fetchError: unknown) {
      updateAnswerSession(questionId, (session) => ({
        ...session,
        answerNotice: { kind: 'error', title: text('评分失败', 'Scoring failed'), message: getErrorMessage(fetchError) },
      }))
    } finally {
      updateAnswerSession(questionId, (session) => ({ ...session, answerScoring: false }))
    }
  }

  async function loadActiveUserErrorTypes() {
    setUserErrorTypesLoading(true)
    try {
      const result = await fetchUserErrorTypes({ learningMode, status: 'ACTIVE', page: 1, size: 100 })
      setUserErrorTypes(result.items)
    } catch (fetchError: unknown) {
      setUserErrorTypes([])
      updateSelectedAnswerSession((session) => ({
        ...session,
        errorConfirmationNotice: { kind: 'error', title: text('无法加载已有复习卡片', 'Could not load review cards'), message: getErrorMessage(fetchError) },
      }))
    } finally {
      setUserErrorTypesLoading(false)
    }
  }

  async function handleConfirmErrors() {
    if (!answerReview || selectedQuestionId === null) return false
    const selectedItems = answerReview.errorAnalysis
      .map((analysis, index) => ({ analysis, candidate: errorCandidates[index], index }))
      .filter(({ candidate }) => candidate?.selected && !candidate.saved)

    if (selectedItems.length === 0) return false

    const payload: UserAnswerErrorConfirmation[] = []
    for (const { analysis, candidate, index } of selectedItems) {
      if (candidate.mode === 'NEW_USER_ERROR_TYPE') {
        if (!candidate.userErrorTypeName.trim() || !candidate.userErrorTypeDescription.trim()) {
          updateSelectedAnswerSession((session) => ({
            ...session,
            errorConfirmationNotice: { kind: 'error', title: text('请补充复习卡片', 'Complete the review card'), message: text('新建复习卡片需要名称和说明。', 'A new review card requires a name and description.') },
          }))
          return false
        }
        payload.push(toNewErrorConfirmation(analysis, candidate, index))
      } else {
        if (!candidate.userErrorTypeId) {
          updateSelectedAnswerSession((session) => ({
            ...session,
            errorConfirmationNotice: { kind: 'error', title: text('请选择已有复习卡片', 'Select a review card'), message: text('添加记录前请选择对应的复习卡片。', 'Select the review card before adding this item.') },
          }))
          return false
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
        errorConfirmationNotice: { kind: 'info', title: text('复习卡片已更新', 'Review cards updated'), message: text(`已添加 ${selectedItems.length} 项复习内容。`, `Added ${selectedItems.length} review item(s).`) },
      }))
      void loadActiveUserErrorTypes()
      return true
    } catch (fetchError: unknown) {
      updateAnswerSession(questionId, (session) => ({
        ...session,
        errorConfirmationNotice: { kind: 'error', title: text('记录失败', 'Save failed'), message: getErrorMessage(fetchError) },
      }))
      return false
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
    if (answerReview?.errorAnalysis.length) void loadActiveUserErrorTypes()
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
    payload.learningMode = learningMode
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
      questionType: shortQuestionType,
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
    <div>
      <div className="practice-grid">
        <section className="surface generator-panel" aria-label={text('题目生成', 'Question generation')}>
          <form className="form-grid" onSubmit={(event) => event.preventDefault()}>
            <label><span>{text('题目数量', 'Questions')}</span><select value={questionCount} onChange={(event) => setQuestionCount(event.target.value)}>{[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
            <label><span>{text('JLPT 等级', 'JLPT')}</span><select value={level} onChange={(event) => setLevel(event.target.value)}>{['N5', 'N4', 'N3', 'N2', 'N1'].map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
            <label><span>{text('难度', 'Difficulty')}</span><select value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>{[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
            <TagCascadeSelect
              tags={practiceTags}
              english={english}
              loading={practiceTagsLoading}
              parentId={sceneParentId}
              tagCode={sceneTagCode}
              parentLabel={text('场景一级', 'Scene')}
              childLabel={text('场景二级', 'Subscene')}
              parentPlaceholder={practiceTagsLoading ? text('加载中', 'Loading') : text('不限场景', 'Any')}
              childPlaceholder={practiceTagsLoading ? text('加载中', 'Loading') : text('不限场景', 'Any')}
              selectParentFirstText={text('请先选择一级场景', 'Select a scene')}
              onParentChange={setSceneParentId}
              onTagChange={setSceneTagCode}
            />
            <label className="wide-field"><span>{text('补充要求', 'Instructions')}</span><input value={extraRequirements} maxLength={500} placeholder={text('使用敬语、指定场景或语法点', 'Honorifics, context, or grammar')} onChange={(event) => setExtraRequirements(event.target.value)} /></label>
            <button type="button" className="primary-button" disabled={questionLoading} onClick={handleRandomQuestion}>{questionRandomizing ? text('抽题中', 'Loading') : text('随机题目', 'Random')}</button>
            <button type="button" className="primary-button" disabled={questionLoading} onClick={handleGenerateQuestion}>{questionGenerating ? text('生成中', 'Generating') : text('生成题目', 'Generate')}</button>
          </form>
          {practiceTagsError ? <div className="error-message" role="alert">{text('标签加载失败：', 'Could not load tags: ')}{practiceTagsError}</div> : null}
        </section>

        <section className="surface question-panel" aria-label={text('题目预览', 'Question preview')}>
          <div className="section-title"><span className="label">{text('题目预览', 'Preview')}</span><strong>{selectedQuestion ? text(`题目 #${selectedQuestion.id}`, `Question #${selectedQuestion.id}`) : text('尚未选择题目', 'No question yet')}</strong></div>
          {generatedQuestions.length > 1 ? <div className="question-selector" aria-label={text('题目选择', 'Question selection')}>{generatedQuestions.map((question, index) => <button key={question.id} type="button" className={selectedQuestionIndex === index ? 'is-selected' : ''} onClick={() => handleSelectQuestion(index)}>{index + 1}</button>)}</div> : null}
          <dl className="question-details">
            <div><dt>{text('中文原文', 'Source')}</dt><dd>{selectedQuestion?.sourceText ?? text('暂无题目', 'No question')}</dd></div>
            <div><dt>{text('语境', 'Context')}</dt><dd>{selectedQuestion?.contextText ?? text('暂无', 'None')}</dd></div>
            <div>
              <dt>{text('语法点', 'Grammar')}</dt>
              <dd className="grammar-point">
                <button
                  type="button"
                  disabled={!selectedQuestion}
                  aria-expanded={grammarPointVisible}
                  onClick={() => setGrammarPointVisible((visible) => !visible)}
                >
                  {grammarPointVisible ? text('隐藏语法点', 'Hide grammar') : text('显示语法点', 'Show grammar')}
                </button>
                {grammarPointVisible ? <span>{selectedQuestion?.grammarPoint ?? text('暂无', 'None')}</span> : null}
              </dd>
            </div>
            <div><dt>{text('标签', 'Tags')}</dt><dd>{selectedQuestion ? <span className="tag-chip-row">{selectedQuestion.tags.map((tag) => <span key={tag.id}>{getTagDisplayName(tag, english)}</span>)}</span> : text('暂无', 'None')}</dd></div>
            <div><dt>{text('难度', 'Difficulty')}</dt><dd>{selectedQuestion ? `${selectedQuestion.level} / ${selectedQuestion.difficulty}` : text('暂无', 'None')}</dd></div>
          </dl>
        </section>

        <section className="surface answer-panel" aria-label={answerSubmitted ? text('评分结果', 'Score result') : text('答案输入', 'Answer input')}>
          <div className="section-title"><span className="label">{answerSubmitted ? text('评分结果', 'Result') : text('作答', 'Answer')}</span><strong>{answerSubmitted ? text('本次作答结果', 'Your result') : text('输入日语答案', 'Japanese answer')}</strong></div>
          {!answerSubmitted ? (
            <>
              {answerInputNotice && (answerInputNotice.kind === 'error' || !selectedQuestion) ? <Notice notice={answerInputNotice} /> : null}
              <textarea aria-label={text('日语答案', 'Japanese answer')} value={answerText} maxLength={2000} disabled={!selectedQuestion} placeholder={selectedQuestion ? text('请输入日语答案', 'Enter your Japanese answer') : text('生成题目后即可作答', 'Generate a question to begin')} onChange={(event) => updateSelectedAnswerSession((session) => ({ ...session, answerText: event.target.value }))} />
              <div className="action-row answer-input-actions"><button type="button" className="primary-button" disabled={!selectedQuestion || answerScoring} onClick={handleSubmitAnswer}>{answerScoring ? text('评分中', 'Scoring') : text('提交答案', 'Submit')}</button><button type="button" disabled={!selectedQuestion && !answerText} onClick={handleClearAnswer}>{text('清空', 'Clear')}</button></div>
            </>
          ) : (
            <div className="answer-result">
              {answerNotice && (!answerReview || answerNotice.kind === 'error') ? <Notice notice={answerNotice} /> : null}
              {answerScoring ? <div className="notice" role="status" aria-live="polite"><strong>{text('评分中', 'Scoring')}</strong><p>{text('正在分析本次作答。', 'Analyzing your answer.')}</p></div> : null}
              <section className="submitted-answer"><span className="label">{text('你的答案', 'Your answer')}</span><p>{answerText}</p></section>
              {answerReview ? <>
                <div className="score-summary"><span>{text('总分', 'Total score')}</span><strong className={scoreToneClassName(answerReview.totalScore)}>{formatScore(answerReview.totalScore)}</strong></div>
                {errorConfirmationOpen ? (
                  <ErrorConfirmationModal
                    analyses={answerReview.errorAnalysis}
                    candidates={errorCandidates}
                    userErrorTypes={userErrorTypes}
                    userErrorTypesLoading={userErrorTypesLoading}
                    notice={errorConfirmationNotice}
                    confirming={errorConfirming}
                    selectedCount={selectedErrorCount}
                    userAnswerId={answerReview.userAnswerId}
                    reviewCardSource={{ kind: 'FIXED', sourceText: selectedQuestion?.sourceText ?? '' }}
                    recommendedExpressions={answerReview.recommendedExpressions.map((item) => item.expression)}
                    onUpdate={updateErrorCandidate}
                    onConfirm={handleConfirmErrors}
                    onCustomSaved={() => void loadActiveUserErrorTypes()}
                    onClose={() => updateSelectedAnswerSession((session) => ({ ...session, errorConfirmationOpen: false }))}
                  />
                ) : null}
                <details className="review-detail"><summary>{text('详细评分说明', 'Detailed score')}</summary><div className="review-result">
                  <dl className="score-grid"><div><dt>{text('语法与词汇', 'Grammar & vocabulary')}</dt><dd className={scoreToneClassName(answerReview.scores.grammarVocabularyScore)}>{answerReview.scores.grammarVocabularyScore}</dd></div><div><dt>{text('自然流畅度', 'Fluency')}</dt><dd className={scoreToneClassName(answerReview.scores.naturalFluencyScore)}>{answerReview.scores.naturalFluencyScore}</dd></div><div><dt>{text('场景适配度', 'Context fit')}</dt><dd className={scoreToneClassName(answerReview.scores.scenarioAdaptationScore)}>{answerReview.scores.scenarioAdaptationScore}</dd></div><div><dt>{text('信息完整性', 'Completeness')}</dt><dd className={scoreToneClassName(answerReview.scores.informationCompletenessScore)}>{answerReview.scores.informationCompletenessScore}</dd></div></dl>
                  <section className="review-section"><strong>{text('总评', 'Overall feedback')}</strong><p>{answerReview.overallComment}</p></section>
                  <dl className="comment-list"><div><dt>{text('语法', 'Grammar')}</dt><dd>{answerReview.comments.grammarComment}</dd></div><div><dt>{text('词汇', 'Vocabulary')}</dt><dd>{answerReview.comments.vocabularyComment}</dd></div><div><dt>{text('自然度', 'Naturalness')}</dt><dd>{answerReview.comments.naturalnessComment}</dd></div><div><dt>{text('场景', 'Context')}</dt><dd>{answerReview.comments.scenarioComment}</dd></div></dl>
                  <ReviewList title={text('候选错误', 'Candidate errors')} emptyText={text('本次未发现明确错误。', 'No clear errors were found.')} items={answerReview.errorAnalysis}>{(item) => <div><span>{item.errorTypeName} / {item.severity}</span><strong>{item.original}</strong><p>{item.issue}</p><p>{item.suggestion}</p></div>}</ReviewList>
                  <ReviewList title={text('修改建议', 'Revision suggestions')} emptyText={text('本次没有额外修改建议。', 'No additional revision suggestions.')} items={answerReview.revisionSuggestions}>{(item) => <p>{item}</p>}</ReviewList>
                  <ReviewList title={text('推荐表达', 'Recommended expressions')} emptyText={text('本次没有推荐表达。', 'No recommended expressions.')} items={answerReview.recommendedExpressions}>{(item) => <div><span>{item.formality}</span><strong>{item.expression}</strong><p>{item.usage}</p><p>{item.note}</p></div>}</ReviewList>
                </div></details>
                <div className="error-record-action">
                  <span>{answerReview.errorAnalysis.length > 0 ? text(`${errorCandidates.filter((candidate) => candidate.saved).length} / ${answerReview.errorAnalysis.length} 项已加入复习卡片`, `${errorCandidates.filter((candidate) => candidate.saved).length} / ${answerReview.errorAnalysis.length} added to review cards`) : text('可手动记录希望继续练习的表达', 'You can manually save an expression for further practice')}</span>
                  <button type="button" className="primary-button" onClick={handleOpenErrorConfirmation}>{text('添加复习卡片', 'Add review card')}</button>
                </div>
              </> : null}
              <div className="action-row"><button type="button" className="primary-button" disabled={answerScoring || errorConfirming} onClick={handleEditAnswer}>{text('修改答案', 'Edit answer')}</button><button type="button" disabled={answerScoring || errorConfirming} onClick={handleClearAnswer}>{text('清空', 'Clear')}</button></div>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

function Notice({ notice }: { notice: PracticeNotice }) {
  return <div className={notice.kind === 'error' ? 'notice is-error' : 'notice'} role={notice.kind === 'error' ? 'alert' : 'status'}><strong>{notice.title}</strong><p>{notice.message}</p></div>
}

function formatScore(score: number) {
  return score.toFixed(2)
}
