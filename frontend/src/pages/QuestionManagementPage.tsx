import { useEffect, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { fetchTags as queryTags } from '../api/tagApi'
import { deleteQuestion, fetchQuestion, fetchQuestions as queryQuestions, parseCodeList, saveQuestion, toggleQuestionEnabled } from '../api/questionApi'
import PageHeader from '../components/PageHeader'
import type { PracticeNotice } from '../types/api'
import type { Tag } from '../types/tag'
import type { Question, QuestionFilterState, QuestionFormState, QuestionPayload } from '../types/question'
import { useLanguage } from '../i18n/LanguageContext'

type QuestionViewMode = 'list' | 'detail' | 'create' | 'edit'

const EMPTY_QUESTION_FORM: QuestionFormState = {
  sourceText: '',
  contextText: '',
  level: 'N4',
  difficulty: '3',
  grammarPoint: '',
  spoken: true,
  business: false,
  exam: false,
  tagCodes: '',
  standardAnswer: '',
  referenceAnswers: '',
}

const INITIAL_QUESTION_FILTERS: QuestionFilterState = {
  questionType: 'TRANSLATION_ZH_TO_JA',
  level: '',
  difficulty: '',
  tagCodes: '',
  sourceType: '',
  enabled: 'true',
  page: 1,
  size: 20,
}

export default function QuestionManagementPage() {
  const { english, shortQuestionType, articleQuestionType, text } = useLanguage()
  const [questions, setQuestions] = useState<Question[]>([])
  const [questionTotal, setQuestionTotal] = useState(0)
  const [questionFilters, setQuestionFilters] = useState<QuestionFilterState>({ ...INITIAL_QUESTION_FILTERS, questionType: shortQuestionType })
  const [questionLoading, setQuestionLoading] = useState(false)
  const [questionError, setQuestionError] = useState<string | null>(null)
  const [questionNotice, setQuestionNotice] = useState<PracticeNotice | null>(null)
  const [viewMode, setViewMode] = useState<QuestionViewMode>('list')
  const [detailQuestion, setDetailQuestion] = useState<Question | null>(null)
  const [editingQuestion, setEditingQuestion] = useState<Question | null>(null)
  const [questionForm, setQuestionForm] = useState<QuestionFormState>(EMPTY_QUESTION_FORM)
  const [questionSaving, setQuestionSaving] = useState(false)
  const [questionActionId, setQuestionActionId] = useState<number | null>(null)
  const [tagOptions, setTagOptions] = useState<Tag[]>([])
  const [genreTagOptions, setGenreTagOptions] = useState<Tag[]>([])

  useEffect(() => {
    const controller = new AbortController()

    async function fetchQuestions() {
      setQuestionLoading(true)
      setQuestionError(null)

      try {
        const result = await queryQuestions(questionFilters, controller.signal)

        setQuestions(result.items)
        setQuestionTotal(result.total)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setQuestions([])
        setQuestionTotal(0)
        setQuestionError(getErrorMessage(fetchError))
      } finally {
        setQuestionLoading(false)
      }
    }

    fetchQuestions()

    return () => {
      controller.abort()
    }
  }, [questionFilters])

  useEffect(() => {
    const controller = new AbortController()

    async function fetchTagOptions() {
      try {
        const [result, genreResult] = await Promise.all([
          queryTags({ enabledOnly: true, page: 1, size: 100 }, controller.signal),
          queryTags({ tagType: 'GENRE', enabledOnly: true, page: 1, size: 100 }, controller.signal),
        ])
        setTagOptions(result.items.filter((tag) => tag.tagType !== 'GENRE'))
        setGenreTagOptions(genreResult.items)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setTagOptions([])
        setGenreTagOptions([])
      }
    }

    fetchTagOptions()

    return () => {
      controller.abort()
    }
  }, [])

  const questionTotalPages = Math.max(Math.ceil(questionTotal / questionFilters.size), 1)
  const questionFirstItemNo = questionTotal === 0 ? 0 : (questionFilters.page - 1) * questionFilters.size + 1
  const questionLastItemNo = Math.min(questionFilters.page * questionFilters.size, questionTotal)
  const editingArticle = viewMode === 'edit' && editingQuestion?.questionType.endsWith('_ARTICLE')

  function updateQuestionFilters(patch: Partial<QuestionFilterState>) {
    setQuestionFilters((current) => ({
      ...current,
      ...patch,
      page: patch.page ?? 1,
    }))
  }

  function refreshQuestions() {
    setQuestionFilters((current) => ({ ...current }))
  }

  async function handleSelectManagedQuestion(questionId: number) {
    setQuestionActionId(questionId)
    setQuestionNotice(null)

    try {
      const question = await fetchQuestion(questionId)
      setDetailQuestion(question)
      setEditingQuestion(null)
      setViewMode('detail')
    } catch (fetchError: unknown) {
      setQuestionNotice({
        kind: 'error',
        title: text('题目详情加载失败', 'Could not load question details'),
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionActionId(null)
    }
  }

  function handleBackToQuestionList() {
    setViewMode('list')
    setQuestionNotice(null)
  }

  function handleStartCreateQuestion() {
    setViewMode('create')
    setQuestionForm(EMPTY_QUESTION_FORM)
    setEditingQuestion(null)
    setQuestionNotice(null)
  }

  function handleStartEditQuestion(question: Question) {
    setViewMode('edit')
    setEditingQuestion(question)
    setQuestionForm(toQuestionForm(question))
    setQuestionNotice(null)
  }

  function updateQuestionForm(patch: Partial<QuestionFormState>) {
    setQuestionForm((current) => ({ ...current, ...patch }))
  }

  function appendQuestionTagCode(code: string) {
    setQuestionForm((current) => {
      const codes = parseCodeList(current.tagCodes)
      if (!codes.includes(code)) {
        codes.push(code)
      }
      return { ...current, tagCodes: codes.join(', ') }
    })
  }

  async function handleSaveQuestion() {
    const payload = buildQuestionPayload(questionForm)
    if (!payload) {
      return
    }

    setQuestionSaving(true)
    setQuestionNotice(null)

    try {
      const isEdit = viewMode === 'edit' && editingQuestion !== null
      const savedQuestion = await saveQuestion(payload, isEdit ? editingQuestion.id : undefined)

      setDetailQuestion(savedQuestion)
      setEditingQuestion(null)
      setViewMode('detail')
      if (savedQuestion) {
        setQuestionForm(toQuestionForm(savedQuestion))
      }
      setQuestionNotice({
        kind: 'info',
        title: isEdit ? text('题目已更新', 'Question updated') : text('题目已创建', 'Question created'),
        message: savedQuestion ? text(`题目 #${savedQuestion.id} 已保存。`, `Question #${savedQuestion.id} was saved.`) : text('后端没有返回题目详情。', 'The server did not return question details.'),
      })
      refreshQuestions()
    } catch (fetchError: unknown) {
      setQuestionNotice({
        kind: 'error',
        title: text('题目保存失败', 'Could not save question'),
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionSaving(false)
    }
  }

  function buildQuestionPayload(form: QuestionFormState): QuestionPayload | null {
    const tagCodes = parseCodeList(form.tagCodes)
    const standardAnswer = form.standardAnswer.trim()
    const questionType = editingArticle ? articleQuestionType : shortQuestionType
    const referenceAnswers = form.referenceAnswers
      .split('\n')
      .map((answer) => answer.trim())
      .filter(Boolean)

    if (
      !form.sourceText.trim()
      || !form.contextText.trim()
      || !form.grammarPoint.trim()
      || tagCodes.length === 0
      || !standardAnswer
    ) {
      setQuestionNotice({
        kind: 'error',
        title: text('表单未填写完整', 'Complete all required fields'),
        message: editingArticle
          ? text('中文文章、语境、生词提示、体裁和日文参考稿都必须填写。', 'English article, context, vocabulary hints, genre, and Japanese reference are required.')
          : text('中文原文、语境、语法点、标签 code 和标准答案都必须填写。', 'English source, context, grammar point, tag code, and standard answer are required.'),
      })
      return null
    }

    if (questionType.endsWith('_ARTICLE')) {
      const sourceSegments = splitArticleSegments(form.sourceText)
      const answerSegments = splitArticleSegments(standardAnswer)
      const genreCodes = tagCodes.filter((code) => genreTagOptions.some((tag) => tag.code === code))
      const sourceLength = english
        ? form.sourceText.trim().split(/\s+/).filter(Boolean).length
        : sourceSegments.join('').replace(/\s/g, '').length
      if (sourceSegments.length === 0 || sourceSegments.length !== answerSegments.length) {
        setQuestionNotice({
          kind: 'error',
          title: text('文章段落不一致', 'Article segments do not match'),
          message: text('中文原文和日文参考稿必须按一句一段填写，并保持相同段落数和顺序。', 'Enter one English and Japanese sentence per paragraph with the same count and order.'),
        })
        return null
      }
      if (sourceLength < (english ? 120 : 150) || sourceLength > (english ? 220 : 300)) {
        setQuestionNotice({ kind: 'error', title: text('文章长度不合法', 'Invalid article length'), message: text('中文文章长度必须为 150 到 300 个非空白字符。', 'The English article must contain 120 to 220 words.') })
        return null
      }
      if (genreCodes.length !== 1 || tagCodes.length !== 1) {
        setQuestionNotice({ kind: 'error', title: text('请选择体裁', 'Select a genre'), message: text('文章题必须且只能选择 1 个体裁标签。', 'An article question must have exactly one genre tag.') })
        return null
      }
    }

    return {
      questionType,
      sourceText: form.sourceText.trim(),
      contextText: form.contextText.trim(),
      level: form.level,
      difficulty: Number(form.difficulty),
      grammarPoint: form.grammarPoint.trim(),
      spoken: form.spoken,
      business: form.business,
      exam: form.exam,
      tagCodes,
      answers: [
        {
          answerText: standardAnswer,
          answerType: 'STANDARD' as const,
          primaryAnswer: true,
          sortOrder: 0,
        },
        ...(!questionType.endsWith('_ARTICLE') ? referenceAnswers.map((answer, index) => ({
          answerText: answer,
          answerType: 'REFERENCE' as const,
          primaryAnswer: false,
          sortOrder: index + 1,
        })) : []),
      ],
    }
  }

  async function handleToggleQuestionEnabled(question: Question) {
    setQuestionActionId(question.id)
    setQuestionNotice(null)

    try {
      await toggleQuestionEnabled(question)
      setQuestionNotice({
        kind: 'info',
        title: question.enabled ? text('题目已停用', 'Question disabled') : text('题目已启用', 'Question enabled'),
        message: text(`题目 #${question.id} 状态已更新。`, `Question #${question.id} status was updated.`),
      })
      if (detailQuestion?.id === question.id) {
        setDetailQuestion({ ...detailQuestion, enabled: !question.enabled })
      }
      if (editingQuestion?.id === question.id) {
        setEditingQuestion({ ...editingQuestion, enabled: !question.enabled })
      }
      refreshQuestions()
    } catch (fetchError: unknown) {
      setQuestionNotice({
        kind: 'error',
        title: text('状态更新失败', 'Could not update status'),
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionActionId(null)
    }
  }

  async function handleDeleteQuestion(question: Question) {
    const confirmed = window.confirm(text(`确认删除题目 #${question.id}？`, `Delete question #${question.id}?`))
    if (!confirmed) {
      return
    }

    setQuestionActionId(question.id)
    setQuestionNotice(null)

    try {
      await deleteQuestion(question.id)
      setQuestionNotice({
        kind: 'info',
        title: text('题目已删除', 'Question deleted'),
        message: text(`题目 #${question.id} 已逻辑删除。`, `Question #${question.id} was deleted.`),
      })
      if (detailQuestion?.id === question.id) {
        setDetailQuestion(null)
        setEditingQuestion(null)
        setViewMode('list')
      }
      refreshQuestions()
    } catch (fetchError: unknown) {
      setQuestionNotice({
        kind: 'error',
        title: text('题目删除失败', 'Could not delete question'),
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionActionId(null)
    }
  }

  return (
          <section className="page-content target-page question-page" aria-label="question management page">
            {viewMode === 'list' ? (
              <PageHeader
                title={text('题目管理', 'Question management')}
                description={text('管理题库中的短句与文章，可筛选并进行查看、编辑、停用或删除。', 'Create, edit, enable, disable, and delete sentence or article questions.')}
                actions={<button type="button" className="primary-button" onClick={handleStartCreateQuestion}>{text('新建短句', 'New sentence')}</button>}
              />
            ) : null}
            {questionNotice ? (
              <div className={questionNotice.kind === 'error' ? 'notice is-error' : 'notice'}>
                <strong>{questionNotice.title}</strong>
                <p>{questionNotice.message}</p>
              </div>
            ) : null}

            {viewMode === 'list' ? (
              <section className="surface question-management-panel target-list-panel" aria-label="question query">
              <form className="question-filter-bar" onSubmit={(event) => event.preventDefault()}>
                <label>
                  <span>{text('题型', 'Question type')}</span>
                  <select value={questionFilters.questionType} onChange={(event) => updateQuestionFilters({ questionType: event.target.value as QuestionFilterState['questionType'] })}>
                    <option value={shortQuestionType}>{text('短句翻译', 'Sentence')}</option>
                    <option value={articleQuestionType}>{text('文章翻译', 'Article')}</option>
                  </select>
                </label>
                <label>
                  <span>{text('JLPT 等级', 'JLPT level')}</span>
                  <select value={questionFilters.level} onChange={(event) => updateQuestionFilters({ level: event.target.value })}>
                    <option value="">{text('全部', 'All')}</option>
                    <option value="N5">N5</option>
                    <option value="N4">N4</option>
                    <option value="N3">N3</option>
                    <option value="N2">N2</option>
                    <option value="N1">N1</option>
                  </select>
                </label>

                <label>
                  <span>{text('难度', 'Difficulty')}</span>
                  <select value={questionFilters.difficulty} onChange={(event) => updateQuestionFilters({ difficulty: event.target.value })}>
                    <option value="">{text('全部', 'All')}</option>
                    <option value="1">1</option>
                    <option value="2">2</option>
                    <option value="3">3</option>
                    <option value="4">4</option>
                    <option value="5">5</option>
                  </select>
                </label>

                <label>
                  <span>{text('来源', 'Source')}</span>
                  <select value={questionFilters.sourceType} onChange={(event) => updateQuestionFilters({ sourceType: event.target.value as QuestionFilterState['sourceType'] })}>
                    <option value="">{text('全部', 'All')}</option>
                    <option value="AI">AI</option>
                    <option value="MANUAL">{text('人工', 'Manual')}</option>
                    <option value="REVIEW_DERIVED">{text('复习衍生', 'Review-derived')}</option>
                  </select>
                </label>

                <label>
                  <span>{text('状态', 'Status')}</span>
                  <select value={questionFilters.enabled} onChange={(event) => updateQuestionFilters({ enabled: event.target.value as QuestionFilterState['enabled'] })}>
                    <option value="true">{text('启用', 'Enabled')}</option>
                    <option value="false">{text('停用', 'Disabled')}</option>
                    <option value="all">{text('全部', 'All')}</option>
                  </select>
                </label>

              </form>

              {questionError ? <div className="error-message">{questionError}</div> : null}
              <div className="table-wrap">
                <table className="responsive-list-table question-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>{text('题型', 'Type')}</th>
                      <th>{text('原文', 'Source text')}</th>
                      <th>{text('等级', 'Level')}</th>
                      <th>{text('来源', 'Source')}</th>
                      <th>{text('状态', 'Status')}</th>
                      <th>{text('标签', 'Tags')}</th>
                      <th>{text('操作', 'Actions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {questions.map((question) => (
                      <tr key={question.id}>
                        <td data-label="ID">{question.id}</td>
                        <td data-label={text('题型', 'Type')}><span className="question-type-badge">{formatQuestionType(question.questionType, english)}</span></td>
                        <td className="table-question-answer-cell" data-label={text('原文', 'Source text')} title={question.sourceText}>{question.sourceText}</td>
                        <td data-label={text('等级', 'Level')}>{question.level} / {question.difficulty}</td>
                        <td data-label={text('来源', 'Source')}><span className={question.sourceType === 'AI' ? 'data-badge is-brand' : 'data-badge'}>{formatQuestionSourceType(question.sourceType, english)}</span></td>
                        <td data-label={text('状态', 'Status')}><span className={question.enabled ? 'data-badge is-success' : 'data-badge'}>{question.enabled ? text('启用', 'Enabled') : text('停用', 'Disabled')}</span></td>
                        <td className="question-tags-cell" data-label={text('标签', 'Tags')}>
                          <span className="tag-chip-row">
                            {question.tags.slice(0, 2).map((tag) => (
                              <span key={tag.id}>{english ? tag.nameEn : tag.name}</span>
                            ))}
                          </span>
                        </td>
                        <td className="question-actions-cell" data-label={text('操作', 'Actions')}>
                          <div className="table-actions">
                            <button
                              type="button"
                              disabled={questionActionId === question.id}
                              onClick={() => handleSelectManagedQuestion(question.id)}
                            >
                              {text('查看', 'View')}
                            </button>
                            <button type="button" onClick={() => handleStartEditQuestion(question)}>
                              {text('编辑', 'Edit')}
                            </button>
                            <button
                              type="button"
                              disabled={questionActionId === question.id}
                              onClick={() => handleToggleQuestionEnabled(question)}
                            >
                              {question.enabled ? text('停用', 'Disable') : text('启用', 'Enable')}
                            </button>
                            <button
                              type="button"
                              className="danger-button"
                              disabled={questionActionId === question.id}
                              onClick={() => handleDeleteQuestion(question)}
                            >
                              {text('删除', 'Delete')}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {!questionLoading && questions.length === 0 ? <p className="empty-state">{text('暂无题目数据', 'No questions')}</p> : null}
              </div>

              <div className="pagination-bar">
                <div className="pagination-summary">
                  <span>
                    {questionLoading
                      ? text('加载中', 'Loading')
                      : text(`第 ${questionFilters.page} / ${questionTotalPages} 页 · ${questionFirstItemNo}-${questionLastItemNo} / ${questionTotal}`, `Page ${questionFilters.page} / ${questionTotalPages} · ${questionFirstItemNo}-${questionLastItemNo} / ${questionTotal}`)}
                  </span>
                  <label className="page-size-field">
                    <span>{text('每页数量', 'Page size')}</span>
                    <select value={questionFilters.size} onChange={(event) => updateQuestionFilters({ size: Number(event.target.value) })}>
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
                    disabled={questionFilters.page <= 1 || questionLoading}
                    onClick={() => updateQuestionFilters({ page: questionFilters.page - 1 })}
                  >
                    {text('上一页', 'Previous')}
                  </button>
                  <button
                    type="button"
                    disabled={questionFilters.page >= questionTotalPages || questionLoading}
                    onClick={() => updateQuestionFilters({ page: questionFilters.page + 1 })}
                  >
                    {text('下一页', 'Next')}
                  </button>
                  <button type="button" disabled={questionLoading} onClick={refreshQuestions}>
                    {text('刷新', 'Refresh')}
                  </button>
                </div>
              </div>
              </section>
            ) : null}

            {viewMode === 'detail' ? (
              <section className="surface question-detail-panel" aria-label="question detail">
                <PageHeader
                  eyebrow={text('详情', 'Details')}
                  title={detailQuestion ? text(`题目 #${detailQuestion.id}`, `Question #${detailQuestion.id}`) : text('题目详情', 'Question details')}
                  actions={<>
                    <button type="button" onClick={handleBackToQuestionList}>{text('返回列表', 'Back to list')}</button>
                    {detailQuestion ? (
                    <>
                      <button type="button" className="primary-button" onClick={() => handleStartEditQuestion(detailQuestion)}>
                        {text('编辑题目', 'Edit question')}
                      </button>
                      <button
                        type="button"
                        disabled={questionActionId === detailQuestion.id}
                        onClick={() => handleToggleQuestionEnabled(detailQuestion)}
                      >
                        {detailQuestion.enabled ? text('停用', 'Disable') : text('启用', 'Enable')}
                      </button>
                      <button
                        type="button"
                        className="danger-button"
                        disabled={questionActionId === detailQuestion.id}
                        onClick={() => handleDeleteQuestion(detailQuestion)}
                      >
                        {text('删除', 'Delete')}
                      </button>
                    </>
                    ) : null}
                  </>}
                />

                {detailQuestion ? (
                  <dl className="question-details">
                    <div>
                      <dt>{text('题型', 'Type')}</dt>
                      <dd>{formatQuestionType(detailQuestion.questionType, english)}</dd>
                    </div>
                    <div>
                      <dt>{text('中文原文', 'English source')}</dt>
                      <dd>{detailQuestion.questionType.endsWith('_ARTICLE')
                        ? <ArticleSegments text={detailQuestion.sourceText} />
                        : detailQuestion.sourceText}</dd>
                    </div>
                    <div>
                      <dt>{text('语境', 'Context')}</dt>
                      <dd>{detailQuestion.contextText}</dd>
                    </div>
                    <div>
                      <dt>{detailQuestion.questionType.endsWith('_ARTICLE') ? text('生词提示', 'Vocabulary hints') : text('语法点', 'Grammar point')}</dt>
                      <dd className={detailQuestion.questionType.endsWith('_ARTICLE') ? 'pre-wrap-text' : undefined}>{detailQuestion.grammarPoint}</dd>
                    </div>
                    <div>
                      <dt>{text('属性', 'Properties')}</dt>
                      <dd>{formatQuestionFlags(detailQuestion, english)}</dd>
                    </div>
                    <div>
                      <dt>{text('标签', 'Tags')}</dt>
                      <dd>
                        <span className="tag-chip-row">
                          {detailQuestion.tags.map((tag) => (
                            <span key={tag.id}>{english ? tag.nameEn : tag.name} / {tag.code}</span>
                          ))}
                        </span>
                      </dd>
                    </div>
                    <div>
                      <dt>{text('答案', 'Answers')}</dt>
                      <dd>
                        <ol className="compact-answer-list">
                          {detailQuestion.answers.map((answer) => (
                            <li key={answer.id}>
                              <span>{answer.answerType === 'STANDARD' ? text('标准', 'Standard') : text('参考', 'Reference')}</span>
                              <strong>{detailQuestion.questionType.endsWith('_ARTICLE')
                                ? <ArticleSegments text={answer.answerText} />
                                : answer.answerText}</strong>
                            </li>
                          ))}
                        </ol>
                      </dd>
                    </div>
                  </dl>
                ) : (
                  <p className="empty-state">{text('从列表中选择题目查看详情。', 'Select a question from the list to view its details.')}</p>
                )}
              </section>
            ) : null}

            {viewMode === 'create' || viewMode === 'edit' ? (
              <section className="surface question-form-panel" aria-label="question form">
                <PageHeader
                  eyebrow={viewMode === 'edit' ? text('整体更新', 'Edit') : text('人工录入', 'Manual entry')}
                  title={viewMode === 'edit' && editingQuestion ? text(`编辑${formatQuestionType(editingQuestion.questionType, false)} #${editingQuestion.id}`, `Edit ${formatQuestionType(editingQuestion.questionType, true)} #${editingQuestion.id}`) : text('新建短句', 'New sentence')}
                  actions={<>
                    <button type="button" onClick={handleBackToQuestionList}>{text('返回列表', 'Back to list')}</button>
                    {viewMode === 'edit' && editingQuestion ? (
                    <button type="button" onClick={() => handleSelectManagedQuestion(editingQuestion.id)}>
                      {text('查看详情', 'View details')}
                    </button>
                    ) : null}
                  </>}
                />

                <form className="question-edit-form" onSubmit={(event) => event.preventDefault()}>
                  {viewMode === 'edit' && editingQuestion ? (
                    <label>
                      <span>{text('题型', 'Type')}</span>
                      <input value={formatQuestionType(editingQuestion.questionType, english)} disabled />
                    </label>
                  ) : null}
                  <label className="wide-field">
                    <span>{editingArticle ? text('中文文章', 'English article') : text('中文原文', 'English source')}</span>
                    <textarea
                      value={questionForm.sourceText}
                      placeholder={editingArticle ? text('按一句一段输入中文文章，句间保留一个空行', 'Enter one English sentence per paragraph, separated by a blank line') : text('输入中文句子', 'Enter an English sentence')}
                      onChange={(event) => updateQuestionForm({ sourceText: event.target.value })}
                    />
                    {editingArticle ? <small className="field-hint">{english
                      ? `${splitArticleSegments(questionForm.sourceText).length} segments; ${questionForm.sourceText.trim().split(/\s+/).filter(Boolean).length} / 120–220 words.`
                      : `当前 ${splitArticleSegments(questionForm.sourceText).length} 段；中文总长 ${questionForm.sourceText.replace(/\s/g, '').length} / 150–300。`}</small> : null}
                  </label>

                  <label className="wide-field">
                    <span>{text('语境说明', 'Context')}</span>
                    <input
                      value={questionForm.contextText}
                      placeholder={text('说明题目使用语境', 'Describe the context')}
                      onChange={(event) => updateQuestionForm({ contextText: event.target.value })}
                    />
                  </label>

                  <label>
                    <span>{text('JLPT 等级', 'JLPT level')}</span>
                    <select value={questionForm.level} onChange={(event) => updateQuestionForm({ level: event.target.value })}>
                      <option value="N5">N5</option>
                      <option value="N4">N4</option>
                      <option value="N3">N3</option>
                      <option value="N2">N2</option>
                      <option value="N1">N1</option>
                    </select>
                  </label>

                  <label>
                    <span>{text('难度', 'Difficulty')}</span>
                    <select value={questionForm.difficulty} onChange={(event) => updateQuestionForm({ difficulty: event.target.value })}>
                      <option value="1">1</option>
                      <option value="2">2</option>
                      <option value="3">3</option>
                      <option value="4">4</option>
                      <option value="5">5</option>
                    </select>
                  </label>

                  <label className="wide-field">
                    <span>{editingArticle ? text('生词提示', 'Vocabulary hints') : text('语法点', 'Grammar point')}</span>
                    {editingArticle ? (
                      <textarea
                        value={questionForm.grammarPoint}
                        maxLength={255}
                        placeholder={text('每行填写“中文词语：日语表达（读音）”', 'One per line: “English term: Japanese expression (reading)”')}
                        onChange={(event) => updateQuestionForm({ grammarPoint: event.target.value })}
                      />
                    ) : (
                      <input
                        value={questionForm.grammarPoint}
                        placeholder={text('例如：予定を表す表現', 'For example: expressions for plans')}
                        onChange={(event) => updateQuestionForm({ grammarPoint: event.target.value })}
                      />
                    )}
                  </label>

                  <div className="checkbox-row wide-field">
                    <label className="checkbox-field">
                      <input
                        type="checkbox"
                        checked={questionForm.spoken}
                        onChange={(event) => updateQuestionForm({ spoken: event.target.checked })}
                      />
                      <span>{text('口语', 'Spoken')}</span>
                    </label>
                    <label className="checkbox-field">
                      <input
                        type="checkbox"
                        checked={questionForm.business}
                        onChange={(event) => updateQuestionForm({ business: event.target.checked })}
                      />
                      <span>{text('商务', 'Business')}</span>
                    </label>
                    <label className="checkbox-field">
                      <input
                        type="checkbox"
                        checked={questionForm.exam}
                        onChange={(event) => updateQuestionForm({ exam: event.target.checked })}
                      />
                      <span>{text('考试', 'Exam')}</span>
                    </label>
                  </div>

                  {editingArticle ? (
                    <label className="wide-field">
                      <span>{text('文章体裁', 'Genre')}</span>
                      <select value={parseCodeList(questionForm.tagCodes)[0] ?? ''} onChange={(event) => updateQuestionForm({ tagCodes: event.target.value })}>
                        <option value="">{text('请选择体裁', 'Select a genre')}</option>
                        {genreTagOptions.map((tag) => <option key={tag.id} value={tag.code}>{english ? tag.nameEn : tag.name} / {tag.code}</option>)}
                      </select>
                    </label>
                  ) : (
                    <>
                      <label className="wide-field">
                        <span>{text('标签 code', 'Tag codes')}</span>
                        <input
                          value={questionForm.tagCodes}
                          placeholder={text('至少包含 1 个场景标签 code，多个用逗号分隔', 'Include at least one scene tag code; separate multiple codes with commas')}
                          onChange={(event) => updateQuestionForm({ tagCodes: event.target.value })}
                        />
                      </label>

                      <div className="tag-option-row wide-field">
                        {tagOptions.slice(0, 16).map((tag) => (
                          <button key={tag.id} type="button" onClick={() => appendQuestionTagCode(tag.code)}>
                            {english ? tag.nameEn : tag.name}
                          </button>
                        ))}
                      </div>
                    </>
                  )}

                  <label className="wide-field">
                    <span>{editingArticle ? text('日文参考稿', 'Japanese reference') : text('标准答案', 'Standard answer')}</span>
                    <textarea
                      value={questionForm.standardAnswer}
                      placeholder={editingArticle ? text('按一句一段输入日文参考稿，并与中文段落顺序一致', 'Enter one Japanese sentence per paragraph in the same order as the English article') : text('输入主标准答案', 'Enter the primary standard answer')}
                      onChange={(event) => updateQuestionForm({ standardAnswer: event.target.value })}
                    />
                    {editingArticle ? <small className="field-hint">{text(`当前 ${splitArticleSegments(questionForm.standardAnswer).length} 段。`, `${splitArticleSegments(questionForm.standardAnswer).length} segments.`)}</small> : null}
                  </label>

                  {!editingArticle ? <label className="wide-field">
                    <span>{text('参考答案', 'Reference answers')}</span>
                    <textarea
                      value={questionForm.referenceAnswers}
                      placeholder={text('可选，每行一个参考答案', 'Optional; one answer per line')}
                      onChange={(event) => updateQuestionForm({ referenceAnswers: event.target.value })}
                    />
                  </label> : null}

                  <div className="action-row wide-field">
                    <button type="button" className="primary-button" disabled={questionSaving} onClick={handleSaveQuestion}>
                      {questionSaving ? text('保存中', 'Saving') : viewMode === 'edit' ? text('保存修改', 'Save changes') : text('创建短句', 'Create sentence')}
                    </button>
                    <button
                      type="button"
                      onClick={viewMode === 'edit' && editingQuestion ? () => handleSelectManagedQuestion(editingQuestion.id) : handleStartCreateQuestion}
                    >
                      {viewMode === 'edit' ? text('取消编辑', 'Cancel editing') : text('清空表单', 'Clear form')}
                    </button>
                  </div>
                </form>
              </section>
            ) : null}
          </section>
  )
}


function toQuestionForm(question: Question): QuestionFormState {
  const standardAnswer = question.answers.find((answer) => answer.answerType === 'STANDARD' && answer.primaryAnswer)
  const referenceAnswers = question.answers
    .filter((answer) => answer.answerType === 'REFERENCE')
    .map((answer) => answer.answerText)
    .join('\n')

  return {
    sourceText: question.sourceText,
    contextText: question.contextText,
    level: question.level,
    difficulty: String(question.difficulty),
    grammarPoint: question.grammarPoint,
    spoken: question.spoken,
    business: question.business,
    exam: question.exam,
    tagCodes: question.tags.map((tag) => tag.code).join(', '),
    standardAnswer: standardAnswer?.answerText ?? '',
    referenceAnswers,
  }
}

function formatQuestionFlags(question: Question, english: boolean) {
  const flags = [
    question.spoken ? (english ? 'Spoken' : '口语') : null,
    question.business ? (english ? 'Business' : '商务') : null,
    question.exam ? (english ? 'Exam' : '考试') : null,
  ].filter(Boolean)

  return `${question.level} / ${english ? 'Difficulty' : '难度'} ${question.difficulty} / ${question.sourceType} / ${question.enabled ? (english ? 'Enabled' : '启用') : (english ? 'Disabled' : '停用')}${flags.length > 0 ? ` / ${flags.join(english ? ', ' : '、')}` : ''}`
}

function formatQuestionSourceType(sourceType: Question['sourceType'], english: boolean) {
  if (sourceType === 'AI') return 'AI'
  if (sourceType === 'REVIEW_DERIVED') return english ? 'Review-derived' : '复习衍生'
  return english ? 'Manual' : '人工'
}

function formatQuestionType(questionType: Question['questionType'], english: boolean) {
  return questionType.endsWith('_ARTICLE') ? (english ? 'Article' : '文章翻译') : (english ? 'Sentence' : '短句翻译')
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
