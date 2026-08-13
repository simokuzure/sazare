import { useEffect, useState } from 'react'
import { getErrorMessage } from '../api/client'
import { fetchTags as queryTags } from '../api/tagApi'
import { deleteQuestion, fetchQuestion, fetchQuestions as queryQuestions, parseCodeList, saveQuestion, toggleQuestionEnabled } from '../api/questionApi'
import type { PracticeNotice } from '../types/api'
import type { Tag } from '../types/tag'
import type { Question, QuestionFilterState, QuestionFormState, QuestionPayload } from '../types/question'

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
  const [questions, setQuestions] = useState<Question[]>([])
  const [questionTotal, setQuestionTotal] = useState(0)
  const [questionFilters, setQuestionFilters] = useState<QuestionFilterState>(INITIAL_QUESTION_FILTERS)
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
  const editingArticle = viewMode === 'edit' && editingQuestion?.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE'

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
        title: '题目详情加载失败',
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
        title: isEdit ? '题目已更新' : '题目已创建',
        message: savedQuestion ? `题目 #${savedQuestion.id} 已保存。` : '后端没有返回题目详情。',
      })
      refreshQuestions()
    } catch (fetchError: unknown) {
      setQuestionNotice({
        kind: 'error',
        title: '题目保存失败',
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionSaving(false)
    }
  }

  function buildQuestionPayload(form: QuestionFormState): QuestionPayload | null {
    const tagCodes = parseCodeList(form.tagCodes)
    const standardAnswer = form.standardAnswer.trim()
    const questionType = editingArticle ? 'TRANSLATION_ZH_TO_JA_ARTICLE' : 'TRANSLATION_ZH_TO_JA'
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
        title: '表单未填写完整',
        message: editingArticle
          ? '中文文章、语境、生词提示、体裁和日文参考稿都必须填写。'
          : '中文原文、语境、语法点、标签 code 和标准答案都必须填写。',
      })
      return null
    }

    if (questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE') {
      const sourceSegments = splitArticleSegments(form.sourceText)
      const answerSegments = splitArticleSegments(standardAnswer)
      const genreCodes = tagCodes.filter((code) => genreTagOptions.some((tag) => tag.code === code))
      const sourceLength = sourceSegments.join('').replace(/\s/g, '').length
      if (sourceSegments.length === 0 || sourceSegments.length !== answerSegments.length) {
        setQuestionNotice({
          kind: 'error',
          title: '文章段落不一致',
          message: '中文原文和日文参考稿必须按一句一段填写，并保持相同段落数和顺序。',
        })
        return null
      }
      if (sourceLength < 150 || sourceLength > 300) {
        setQuestionNotice({ kind: 'error', title: '文章长度不合法', message: '中文文章长度必须为 150 到 300 个非空白字符。' })
        return null
      }
      if (genreCodes.length !== 1 || tagCodes.length !== 1) {
        setQuestionNotice({ kind: 'error', title: '请选择体裁', message: '文章题必须且只能选择 1 个体裁标签。' })
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
        ...(questionType === 'TRANSLATION_ZH_TO_JA' ? referenceAnswers.map((answer, index) => ({
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
        title: question.enabled ? '题目已停用' : '题目已启用',
        message: `题目 #${question.id} 状态已更新。`,
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
        title: '状态更新失败',
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionActionId(null)
    }
  }

  async function handleDeleteQuestion(question: Question) {
    const confirmed = window.confirm(`确认删除题目 #${question.id}？`)
    if (!confirmed) {
      return
    }

    setQuestionActionId(question.id)
    setQuestionNotice(null)

    try {
      await deleteQuestion(question.id)
      setQuestionNotice({
        kind: 'info',
        title: '题目已删除',
        message: `题目 #${question.id} 已逻辑删除。`,
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
        title: '题目删除失败',
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionActionId(null)
    }
  }

  return (
          <section className="page-content" aria-label="question management page">
            {questionNotice ? (
              <div className={questionNotice.kind === 'error' ? 'notice is-error' : 'notice'}>
                <strong>{questionNotice.title}</strong>
                <p>{questionNotice.message}</p>
              </div>
            ) : null}

            {viewMode === 'list' ? (
              <section className="surface question-management-panel" aria-label="question query">
              <form className="question-filter-bar" onSubmit={(event) => event.preventDefault()}>
                <label>
                  <span>题型</span>
                  <select value={questionFilters.questionType} onChange={(event) => updateQuestionFilters({ questionType: event.target.value as QuestionFilterState['questionType'] })}>
                    <option value="TRANSLATION_ZH_TO_JA">短句翻译</option>
                    <option value="TRANSLATION_ZH_TO_JA_ARTICLE">文章翻译</option>
                  </select>
                </label>
                <label>
                  <span>JLPT 等级</span>
                  <select value={questionFilters.level} onChange={(event) => updateQuestionFilters({ level: event.target.value })}>
                    <option value="">全部</option>
                    <option value="N5">N5</option>
                    <option value="N4">N4</option>
                    <option value="N3">N3</option>
                    <option value="N2">N2</option>
                    <option value="N1">N1</option>
                  </select>
                </label>

                <label>
                  <span>难度</span>
                  <select value={questionFilters.difficulty} onChange={(event) => updateQuestionFilters({ difficulty: event.target.value })}>
                    <option value="">全部</option>
                    <option value="1">1</option>
                    <option value="2">2</option>
                    <option value="3">3</option>
                    <option value="4">4</option>
                    <option value="5">5</option>
                  </select>
                </label>

                <label>
                  <span>来源</span>
                  <select value={questionFilters.sourceType} onChange={(event) => updateQuestionFilters({ sourceType: event.target.value as QuestionFilterState['sourceType'] })}>
                    <option value="">全部</option>
                    <option value="AI">AI</option>
                    <option value="MANUAL">人工</option>
                    <option value="REVIEW_DERIVED">复习衍生</option>
                  </select>
                </label>

                <label>
                  <span>状态</span>
                  <select value={questionFilters.enabled} onChange={(event) => updateQuestionFilters({ enabled: event.target.value as QuestionFilterState['enabled'] })}>
                    <option value="true">启用</option>
                    <option value="false">停用</option>
                    <option value="all">全部</option>
                  </select>
                </label>

                <label>
                  <span>标签 code</span>
                  <input
                    value={questionFilters.tagCodes}
                    placeholder="多个 code 用逗号分隔"
                    onChange={(event) => updateQuestionFilters({ tagCodes: event.target.value })}
                  />
                </label>

              </form>

              {questionError ? <div className="error-message">{questionError}</div> : null}
              <div className="table-wrap">
                <table className="responsive-list-table question-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>题型</th>
                      <th>原文</th>
                      <th>等级</th>
                      <th>来源</th>
                      <th>状态</th>
                      <th>标签</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {questions.map((question) => (
                      <tr key={question.id}>
                        <td data-label="ID">{question.id}</td>
                        <td data-label="题型"><span className="question-type-badge">{formatQuestionType(question.questionType)}</span></td>
                        <td className="table-question-answer-cell" data-label="原文" title={question.sourceText}>{question.sourceText}</td>
                        <td data-label="等级">{question.level} / {question.difficulty}</td>
                        <td data-label="来源">{formatQuestionSourceType(question.sourceType)}</td>
                        <td data-label="状态">{question.enabled ? '启用' : '停用'}</td>
                        <td className="question-tags-cell" data-label="标签">
                          <span className="tag-chip-row">
                            {question.tags.slice(0, 3).map((tag) => (
                              <span key={tag.id}>{tag.name}</span>
                            ))}
                            {question.tags.length > 3 ? <span>+{question.tags.length - 3}</span> : null}
                          </span>
                        </td>
                        <td className="question-actions-cell" data-label="操作">
                          <div className="table-actions">
                            <button
                              type="button"
                              disabled={questionActionId === question.id}
                              onClick={() => handleSelectManagedQuestion(question.id)}
                            >
                              查看
                            </button>
                            <button type="button" onClick={() => handleStartEditQuestion(question)}>
                              编辑
                            </button>
                            <button
                              type="button"
                              disabled={questionActionId === question.id}
                              onClick={() => handleToggleQuestionEnabled(question)}
                            >
                              {question.enabled ? '停用' : '启用'}
                            </button>
                            <button
                              type="button"
                              className="danger-button"
                              disabled={questionActionId === question.id}
                              onClick={() => handleDeleteQuestion(question)}
                            >
                              删除
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {!questionLoading && questions.length === 0 ? <p className="empty-state">暂无题目数据</p> : null}
              </div>

              <div className="pagination-bar">
                <div className="pagination-summary">
                  <span>
                    {questionLoading
                      ? '加载中'
                      : `第 ${questionFilters.page} / ${questionTotalPages} 页 · ${questionFirstItemNo}-${questionLastItemNo} / ${questionTotal}`}
                  </span>
                  <label className="page-size-field">
                    <span>每页数量</span>
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
                    上一页
                  </button>
                  <button
                    type="button"
                    disabled={questionFilters.page >= questionTotalPages || questionLoading}
                    onClick={() => updateQuestionFilters({ page: questionFilters.page + 1 })}
                  >
                    下一页
                  </button>
                  <button type="button" disabled={questionLoading} onClick={refreshQuestions}>
                    刷新
                  </button>
                  <button type="button" className="primary-button" onClick={handleStartCreateQuestion}>
                    新建短句
                  </button>
                </div>
              </div>
              </section>
            ) : null}

            {viewMode === 'detail' ? (
              <section className="surface question-detail-panel" aria-label="question detail">
                <div className="section-title">
                  <span className="label">详情</span>
                  <strong>{detailQuestion ? `题目 #${detailQuestion.id}` : '未选择题目'}</strong>
                </div>

                <div className="action-row">
                  <button type="button" onClick={handleBackToQuestionList}>
                    返回列表
                  </button>
                  {detailQuestion ? (
                    <>
                      <button type="button" className="primary-button" onClick={() => handleStartEditQuestion(detailQuestion)}>
                        编辑题目
                      </button>
                      <button
                        type="button"
                        disabled={questionActionId === detailQuestion.id}
                        onClick={() => handleToggleQuestionEnabled(detailQuestion)}
                      >
                        {detailQuestion.enabled ? '停用' : '启用'}
                      </button>
                      <button
                        type="button"
                        className="danger-button"
                        disabled={questionActionId === detailQuestion.id}
                        onClick={() => handleDeleteQuestion(detailQuestion)}
                      >
                        删除
                      </button>
                    </>
                  ) : null}
                </div>

                {detailQuestion ? (
                  <dl className="question-details">
                    <div>
                      <dt>题型</dt>
                      <dd>{formatQuestionType(detailQuestion.questionType)}</dd>
                    </div>
                    <div>
                      <dt>中文原文</dt>
                      <dd>{detailQuestion.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE'
                        ? <ArticleSegments text={detailQuestion.sourceText} />
                        : detailQuestion.sourceText}</dd>
                    </div>
                    <div>
                      <dt>语境</dt>
                      <dd>{detailQuestion.contextText}</dd>
                    </div>
                    <div>
                      <dt>{detailQuestion.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '生词提示' : '语法点'}</dt>
                      <dd className={detailQuestion.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? 'pre-wrap-text' : undefined}>{detailQuestion.grammarPoint}</dd>
                    </div>
                    <div>
                      <dt>属性</dt>
                      <dd>{formatQuestionFlags(detailQuestion)}</dd>
                    </div>
                    <div>
                      <dt>标签</dt>
                      <dd>
                        <span className="tag-chip-row">
                          {detailQuestion.tags.map((tag) => (
                            <span key={tag.id}>{tag.name} / {tag.code}</span>
                          ))}
                        </span>
                      </dd>
                    </div>
                    <div>
                      <dt>答案</dt>
                      <dd>
                        <ol className="compact-answer-list">
                          {detailQuestion.answers.map((answer) => (
                            <li key={answer.id}>
                              <span>{answer.answerType === 'STANDARD' ? '标准' : '参考'}</span>
                              <strong>{detailQuestion.questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE'
                                ? <ArticleSegments text={answer.answerText} />
                                : answer.answerText}</strong>
                            </li>
                          ))}
                        </ol>
                      </dd>
                    </div>
                  </dl>
                ) : (
                  <p className="empty-state">从列表中选择题目查看详情。</p>
                )}
              </section>
            ) : null}

            {viewMode === 'create' || viewMode === 'edit' ? (
              <section className="surface question-form-panel" aria-label="question form">
                <div className="section-title">
                  <span className="label">{viewMode === 'edit' ? '整体更新' : '人工录入'}</span>
                  <strong>{viewMode === 'edit' && editingQuestion ? `编辑${formatQuestionType(editingQuestion.questionType)} #${editingQuestion.id}` : '新建短句'}</strong>
                </div>

                <div className="action-row">
                  <button type="button" onClick={handleBackToQuestionList}>
                    返回列表
                  </button>
                  {viewMode === 'edit' && editingQuestion ? (
                    <button type="button" onClick={() => handleSelectManagedQuestion(editingQuestion.id)}>
                      查看详情
                    </button>
                  ) : null}
                </div>

                <form className="question-edit-form" onSubmit={(event) => event.preventDefault()}>
                  {viewMode === 'edit' && editingQuestion ? (
                    <label>
                      <span>题型</span>
                      <input value={formatQuestionType(editingQuestion.questionType)} disabled />
                    </label>
                  ) : null}
                  <label className="wide-field">
                    <span>{editingArticle ? '中文文章' : '中文原文'}</span>
                    <textarea
                      value={questionForm.sourceText}
                      placeholder={editingArticle ? '按一句一段输入中文文章，句间保留一个空行' : '输入中文句子'}
                      onChange={(event) => updateQuestionForm({ sourceText: event.target.value })}
                    />
                    {editingArticle ? <small className="field-hint">当前 {splitArticleSegments(questionForm.sourceText).length} 段；中文总长 {questionForm.sourceText.replace(/\s/g, '').length} / 150–300。</small> : null}
                  </label>

                  <label className="wide-field">
                    <span>语境说明</span>
                    <input
                      value={questionForm.contextText}
                      placeholder="说明题目使用语境"
                      onChange={(event) => updateQuestionForm({ contextText: event.target.value })}
                    />
                  </label>

                  <label>
                    <span>JLPT 等级</span>
                    <select value={questionForm.level} onChange={(event) => updateQuestionForm({ level: event.target.value })}>
                      <option value="N5">N5</option>
                      <option value="N4">N4</option>
                      <option value="N3">N3</option>
                      <option value="N2">N2</option>
                      <option value="N1">N1</option>
                    </select>
                  </label>

                  <label>
                    <span>难度</span>
                    <select value={questionForm.difficulty} onChange={(event) => updateQuestionForm({ difficulty: event.target.value })}>
                      <option value="1">1</option>
                      <option value="2">2</option>
                      <option value="3">3</option>
                      <option value="4">4</option>
                      <option value="5">5</option>
                    </select>
                  </label>

                  <label className="wide-field">
                    <span>{editingArticle ? '生词提示' : '语法点'}</span>
                    {editingArticle ? (
                      <textarea
                        value={questionForm.grammarPoint}
                        maxLength={255}
                        placeholder="每行填写“中文词语：日语表达（读音）”"
                        onChange={(event) => updateQuestionForm({ grammarPoint: event.target.value })}
                      />
                    ) : (
                      <input
                        value={questionForm.grammarPoint}
                        placeholder="例如：予定を表す表現"
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
                      <span>口语</span>
                    </label>
                    <label className="checkbox-field">
                      <input
                        type="checkbox"
                        checked={questionForm.business}
                        onChange={(event) => updateQuestionForm({ business: event.target.checked })}
                      />
                      <span>商务</span>
                    </label>
                    <label className="checkbox-field">
                      <input
                        type="checkbox"
                        checked={questionForm.exam}
                        onChange={(event) => updateQuestionForm({ exam: event.target.checked })}
                      />
                      <span>考试</span>
                    </label>
                  </div>

                  {editingArticle ? (
                    <label className="wide-field">
                      <span>文章体裁</span>
                      <select value={parseCodeList(questionForm.tagCodes)[0] ?? ''} onChange={(event) => updateQuestionForm({ tagCodes: event.target.value })}>
                        <option value="">请选择体裁</option>
                        {genreTagOptions.map((tag) => <option key={tag.id} value={tag.code}>{tag.name} / {tag.code}</option>)}
                      </select>
                    </label>
                  ) : (
                    <>
                      <label className="wide-field">
                        <span>标签 code</span>
                        <input
                          value={questionForm.tagCodes}
                          placeholder="至少包含 1 个场景标签 code，多个用逗号分隔"
                          onChange={(event) => updateQuestionForm({ tagCodes: event.target.value })}
                        />
                      </label>

                      <div className="tag-option-row wide-field">
                        {tagOptions.slice(0, 16).map((tag) => (
                          <button key={tag.id} type="button" onClick={() => appendQuestionTagCode(tag.code)}>
                            {tag.name}
                          </button>
                        ))}
                      </div>
                    </>
                  )}

                  <label className="wide-field">
                    <span>{editingArticle ? '日文参考稿' : '标准答案'}</span>
                    <textarea
                      value={questionForm.standardAnswer}
                      placeholder={editingArticle ? '按一句一段输入日文参考稿，并与中文段落顺序一致' : '输入主标准答案'}
                      onChange={(event) => updateQuestionForm({ standardAnswer: event.target.value })}
                    />
                    {editingArticle ? <small className="field-hint">当前 {splitArticleSegments(questionForm.standardAnswer).length} 段。</small> : null}
                  </label>

                  {!editingArticle ? <label className="wide-field">
                    <span>参考答案</span>
                    <textarea
                      value={questionForm.referenceAnswers}
                      placeholder="可选，每行一个参考答案"
                      onChange={(event) => updateQuestionForm({ referenceAnswers: event.target.value })}
                    />
                  </label> : null}

                  <div className="action-row wide-field">
                    <button type="button" className="primary-button" disabled={questionSaving} onClick={handleSaveQuestion}>
                      {questionSaving ? '保存中' : viewMode === 'edit' ? '保存修改' : '创建短句'}
                    </button>
                    <button
                      type="button"
                      onClick={viewMode === 'edit' && editingQuestion ? () => handleSelectManagedQuestion(editingQuestion.id) : handleStartCreateQuestion}
                    >
                      {viewMode === 'edit' ? '取消编辑' : '清空表单'}
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

function formatQuestionFlags(question: Question) {
  const flags = [
    question.spoken ? '口语' : null,
    question.business ? '商务' : null,
    question.exam ? '考试' : null,
  ].filter(Boolean)

  return `${question.level} / 难度 ${question.difficulty} / ${question.sourceType} / ${question.enabled ? '启用' : '停用'}${flags.length > 0 ? ` / ${flags.join('、')}` : ''}`
}

function formatQuestionSourceType(sourceType: Question['sourceType']) {
  if (sourceType === 'AI') return 'AI'
  if (sourceType === 'REVIEW_DERIVED') return '复习衍生'
  return '人工'
}

function formatQuestionType(questionType: Question['questionType']) {
  return questionType === 'TRANSLATION_ZH_TO_JA_ARTICLE' ? '文章翻译' : '短句翻译'
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
