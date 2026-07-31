import { type ReactNode, useEffect, useMemo, useState } from 'react'
import './App.css'

type HealthResponse = {
  code: number
  message: string
  data: {
    status: string
    service: string
    timestamp: string
  } | null
}

type ApiResponse<T> = {
  code: number
  message: string
  data: T | null
}

type PageKey = 'practice' | 'tags' | 'questions' | 'reviews'
type TagFilter = '' | 'SCENE' | 'FUNCTION'
type NoticeKind = 'info' | 'error'

type Tag = {
  id: number
  tagType: 'SCENE' | 'FUNCTION'
  parentId: number | null
  code: string
  name: string
  description: string | null
  sortOrder: number
}

type QuestionAnswer = {
  id: number
  answerText: string
  answerType: 'STANDARD' | 'REFERENCE'
  primaryAnswer: boolean
  sortOrder: number
}

type Question = {
  id: number
  questionType: 'TRANSLATION_ZH_TO_JA'
  sourceText: string
  contextText: string
  level: string
  difficulty: number
  grammarPoint: string
  spoken: boolean
  business: boolean
  exam: boolean
  sourceType: 'AI' | 'MANUAL'
  enabled: boolean
  tags: Tag[]
  answers: QuestionAnswer[]
  createdAt: string
  updatedAt: string
}

type AnswerScores = {
  grammarVocabularyScore: number
  naturalFluencyScore: number
  scenarioAdaptationScore: number
  informationCompletenessScore: number
}

type AnswerReviewComments = {
  grammarComment: string
  vocabularyComment: string
  naturalnessComment: string
  scenarioComment: string
}

type AnswerErrorAnalysis = {
  type: 'GRAMMAR' | 'VOCABULARY' | 'NATURALNESS' | 'HONORIFIC' | 'SCENARIO' | 'COMPLETENESS'
  original: string
  issue: string
  suggestion: string
  severity: 'LOW' | 'MEDIUM' | 'HIGH'
}

type AnswerRecommendedExpression = {
  expression: string
  usage: string
  formality: 'CASUAL' | 'NEUTRAL' | 'POLITE' | 'BUSINESS'
  note: string
}

type AnswerReview = {
  userAnswerId: number
  questionId: number
  answerText: string
  answerStatus: 'SUBMITTED' | 'REVIEWED' | 'FAILED'
  scores: AnswerScores
  totalScore: number
  overallComment: string
  comments: AnswerReviewComments
  errorAnalysis: AnswerErrorAnalysis[]
  revisionSuggestions: string[]
  recommendedExpressions: AnswerRecommendedExpression[]
  createdAt: string
  updatedAt: string
}

type PageData<T> = {
  items: T[]
  page: number
  size: number
  total: number
}

type PracticeNotice = {
  kind: NoticeKind
  title: string
  message: string
}

type QuestionFormMode = 'create' | 'edit'

type QuestionFormState = {
  sourceText: string
  contextText: string
  level: string
  difficulty: string
  grammarPoint: string
  spoken: boolean
  business: boolean
  exam: boolean
  tagCodes: string
  standardAnswer: string
  referenceAnswers: string
}

type QuestionFilterState = {
  level: string
  difficulty: string
  tagCodes: string
  sourceType: '' | 'AI' | 'MANUAL'
  enabled: 'true' | 'false' | 'all'
  page: number
  size: number
}

const NAV_ITEMS: { key: PageKey; label: string }[] = [
  { key: 'practice', label: '练习' },
  { key: 'tags', label: '标签管理' },
  { key: 'questions', label: '问题管理' },
  { key: 'reviews', label: '错题复习' },
]

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
  level: '',
  difficulty: '',
  tagCodes: '',
  sourceType: '',
  enabled: 'true',
  page: 1,
  size: 20,
}

function App() {
  const [activePage, setActivePage] = useState<PageKey>('practice')
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [healthError, setHealthError] = useState<string | null>(null)

  const [practiceTags, setPracticeTags] = useState<Tag[]>([])
  const [practiceTagsLoading, setPracticeTagsLoading] = useState(false)
  const [practiceTagsError, setPracticeTagsError] = useState<string | null>(null)
  const [questionCount, setQuestionCount] = useState('1')
  const [level, setLevel] = useState('')
  const [difficulty, setDifficulty] = useState('3')
  const [sceneTagCode, setSceneTagCode] = useState('')
  const [functionTagCode, setFunctionTagCode] = useState('')
  const [extraRequirements, setExtraRequirements] = useState('')
  const [generatedQuestions, setGeneratedQuestions] = useState<Question[]>([])
  const [selectedQuestionIndex, setSelectedQuestionIndex] = useState(0)
  const [questionGenerating, setQuestionGenerating] = useState(false)
  const [answerText, setAnswerText] = useState('')
  const [answerScoring, setAnswerScoring] = useState(false)
  const [answerReview, setAnswerReview] = useState<AnswerReview | null>(null)
  const [practiceNotice, setPracticeNotice] = useState<PracticeNotice | null>(null)

  const [tags, setTags] = useState<Tag[]>([])
  const [total, setTotal] = useState(0)
  const [tagType, setTagType] = useState<TagFilter>('')
  const [parentId, setParentId] = useState('')
  const [enabledOnly, setEnabledOnly] = useState(true)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [tagLoading, setTagLoading] = useState(false)
  const [tagError, setTagError] = useState<string | null>(null)

  const [questions, setQuestions] = useState<Question[]>([])
  const [questionTotal, setQuestionTotal] = useState(0)
  const [questionFilters, setQuestionFilters] = useState<QuestionFilterState>(INITIAL_QUESTION_FILTERS)
  const [questionLoading, setQuestionLoading] = useState(false)
  const [questionError, setQuestionError] = useState<string | null>(null)
  const [questionNotice, setQuestionNotice] = useState<PracticeNotice | null>(null)
  const [selectedManagedQuestion, setSelectedManagedQuestion] = useState<Question | null>(null)
  const [questionFormMode, setQuestionFormMode] = useState<QuestionFormMode>('create')
  const [questionForm, setQuestionForm] = useState<QuestionFormState>(EMPTY_QUESTION_FORM)
  const [questionSaving, setQuestionSaving] = useState(false)
  const [questionActionId, setQuestionActionId] = useState<number | null>(null)

  useEffect(() => {
    fetch('/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
        return response.json() as Promise<HealthResponse>
      })
      .then((data) => {
        setHealth(data)
        setHealthError(null)
      })
      .catch((fetchError: unknown) => {
        setHealthError(fetchError instanceof Error ? fetchError.message : '请求失败')
      })
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    async function fetchPracticeTags() {
      setPracticeTagsLoading(true)
      setPracticeTagsError(null)

      const searchParams = new URLSearchParams({
        enabledOnly: 'true',
        page: '1',
        size: '100',
      })

      try {
        const response = await fetch(`/api/tags?${searchParams.toString()}`, {
          signal: controller.signal,
        })
        const result = await readApiResponse<PageData<Tag>>(response)

        setPracticeTags(result.data?.items ?? [])
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setPracticeTags([])
        setPracticeTagsError(fetchError instanceof Error ? fetchError.message : '请求失败')
      } finally {
        setPracticeTagsLoading(false)
      }
    }

    fetchPracticeTags()

    return () => {
      controller.abort()
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    async function fetchTags() {
      setTagLoading(true)
      setTagError(null)

      const searchParams = new URLSearchParams()
      if (tagType) {
        searchParams.set('tagType', tagType)
      }
      if (parentId.trim()) {
        searchParams.set('parentId', parentId.trim())
      }
      searchParams.set('enabledOnly', String(enabledOnly))
      searchParams.set('page', String(page))
      searchParams.set('size', String(size))

      try {
        const response = await fetch(`/api/tags?${searchParams.toString()}`, {
          signal: controller.signal,
        })
        const result = await readApiResponse<PageData<Tag>>(response)

        setTags(result.data?.items ?? [])
        setTotal(result.data?.total ?? 0)
      } catch (fetchError: unknown) {
        if (fetchError instanceof DOMException && fetchError.name === 'AbortError') {
          return
        }
        setTags([])
        setTotal(0)
        setTagError(fetchError instanceof Error ? fetchError.message : '请求失败')
      } finally {
        setTagLoading(false)
      }
    }

    fetchTags()

    return () => {
      controller.abort()
    }
  }, [tagType, parentId, enabledOnly, page, size])

  useEffect(() => {
    setPage(1)
  }, [tagType, parentId, enabledOnly, size])

  useEffect(() => {
    if (activePage !== 'questions') {
      return
    }

    const controller = new AbortController()

    async function fetchQuestions() {
      setQuestionLoading(true)
      setQuestionError(null)

      const searchParams = new URLSearchParams({
        questionType: 'TRANSLATION_ZH_TO_JA',
        page: String(questionFilters.page),
        size: String(questionFilters.size),
      })
      if (questionFilters.level) {
        searchParams.set('level', questionFilters.level)
      }
      if (questionFilters.difficulty) {
        searchParams.set('difficulty', questionFilters.difficulty)
      }
      if (questionFilters.sourceType) {
        searchParams.set('sourceType', questionFilters.sourceType)
      }
      if (questionFilters.enabled !== 'all') {
        searchParams.set('enabled', questionFilters.enabled)
      }
      const filterTagCodes = parseCodeList(questionFilters.tagCodes)
      if (filterTagCodes.length > 0) {
        searchParams.set('tagCodes', filterTagCodes.join(','))
      }

      try {
        const response = await fetch(`/api/questions?${searchParams.toString()}`, {
          signal: controller.signal,
        })
        const result = await readApiResponse<PageData<Question>>(response)

        setQuestions(result.data?.items ?? [])
        setQuestionTotal(result.data?.total ?? 0)
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
  }, [activePage, questionFilters])

  const sceneTags = useMemo(() => practiceTags.filter((tag) => tag.tagType === 'SCENE'), [practiceTags])
  const functionTags = useMemo(() => practiceTags.filter((tag) => tag.tagType === 'FUNCTION'), [practiceTags])
  const backendStatus = health?.data?.status ?? 'UNKNOWN'
  const sceneCount = tags.filter((tag) => tag.tagType === 'SCENE').length
  const functionCount = tags.filter((tag) => tag.tagType === 'FUNCTION').length
  const totalPages = Math.max(Math.ceil(total / size), 1)
  const firstItemNo = total === 0 ? 0 : (page - 1) * size + 1
  const lastItemNo = Math.min(page * size, total)
  const selectedQuestion = generatedQuestions[selectedQuestionIndex] ?? null
  const questionTotalPages = Math.max(Math.ceil(questionTotal / questionFilters.size), 1)
  const questionFirstItemNo = questionTotal === 0 ? 0 : (questionFilters.page - 1) * questionFilters.size + 1
  const questionLastItemNo = Math.min(questionFilters.page * questionFilters.size, questionTotal)
  const enabledQuestionCount = questions.filter((question) => question.enabled).length
  const manualQuestionCount = questions.filter((question) => question.sourceType === 'MANUAL').length
  const tagOptions = practiceTags

  async function handleGenerateQuestion() {
    setQuestionGenerating(true)
    setPracticeNotice(null)

    try {
      const response = await fetch('/api/questions/ai-generations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(buildAiQuestionGenerationPayload()),
      })
      const result = await readApiResponse<Question[]>(response)

      const questions = result.data ?? []
      setGeneratedQuestions(questions)
      setSelectedQuestionIndex(0)
      setAnswerText('')
      setAnswerReview(null)
      setPracticeNotice({
        kind: 'info',
        title: questions.length > 0 ? '题目已生成' : '没有返回题目',
        message: questions.length > 0 ? `已生成 ${questions.length} 道题。` : '后端没有返回可展示的题目。',
      })
    } catch (fetchError: unknown) {
      setGeneratedQuestions([])
      setSelectedQuestionIndex(0)
      setPracticeNotice({
        kind: 'error',
        title: '题目生成失败',
        message: getErrorMessage(fetchError),
      })
    } finally {
      setQuestionGenerating(false)
    }
  }

  function handleSelectQuestion(index: number) {
    setSelectedQuestionIndex(index)
    setAnswerText('')
    setAnswerReview(null)
    setPracticeNotice(null)
  }

  async function handleSubmitAnswer() {
    if (!selectedQuestion) {
      setPracticeNotice({
        kind: 'error',
        title: '请先生成题目',
        message: '生成题目后再填写日语回答。',
      })
      return
    }

    if (!answerText.trim()) {
      setPracticeNotice({
        kind: 'error',
        title: '请先输入答案',
        message: '提交前需要填写日语回答。',
      })
      return
    }

    setAnswerScoring(true)
    setAnswerReview(null)
    setPracticeNotice(null)

    try {
      const response = await fetch(`/api/questions/${selectedQuestion.id}/answers`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ answerText: answerText.trim() }),
      })
      const result = await readApiResponse<AnswerReview>(response)

      setAnswerReview(result.data)
      setPracticeNotice({
        kind: 'info',
        title: '评分完成',
        message: result.data ? `本次总分 ${formatScore(result.data.totalScore)}。` : '后端没有返回评分结果。',
      })
    } catch (fetchError: unknown) {
      setPracticeNotice({
        kind: 'error',
        title: '评分失败',
        message: getErrorMessage(fetchError),
      })
    } finally {
      setAnswerScoring(false)
    }
  }

  function handleClearAnswer() {
    setAnswerText('')
    setAnswerReview(null)
    setPracticeNotice(null)
  }

  function buildAiQuestionGenerationPayload() {
    const payload: {
      questionCount?: number
      level?: string
      difficulty?: number
      sceneTagCodes?: string[]
      functionTagCodes?: string[]
      excludedSourceTexts?: string[]
      extraRequirements?: string
    } = {}

    if (questionCount) {
      payload.questionCount = Number(questionCount)
    }
    if (level) {
      payload.level = level
    }
    if (difficulty) {
      payload.difficulty = Number(difficulty)
    }
    if (sceneTagCode) {
      payload.sceneTagCodes = [sceneTagCode]
    }
    if (functionTagCode) {
      payload.functionTagCodes = [functionTagCode]
    }
    if (generatedQuestions.length > 0) {
      payload.excludedSourceTexts = generatedQuestions.map((question) => question.sourceText)
    }
    if (extraRequirements.trim()) {
      payload.extraRequirements = extraRequirements.trim()
    }

    return payload
  }

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
      const response = await fetch(`/api/questions/${questionId}`)
      const result = await readApiResponse<Question>(response)
      setSelectedManagedQuestion(result.data)
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

  function handleStartCreateQuestion() {
    setQuestionFormMode('create')
    setQuestionForm(EMPTY_QUESTION_FORM)
    setSelectedManagedQuestion(null)
    setQuestionNotice(null)
  }

  function handleStartEditQuestion(question: Question) {
    setQuestionFormMode('edit')
    setSelectedManagedQuestion(question)
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
      const isEdit = questionFormMode === 'edit' && selectedManagedQuestion
      const response = await fetch(isEdit ? `/api/questions/${selectedManagedQuestion.id}` : '/api/questions', {
        method: isEdit ? 'PUT' : 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      })
      const result = await readApiResponse<Question>(response)

      setSelectedManagedQuestion(result.data)
      setQuestionFormMode('edit')
      if (result.data) {
        setQuestionForm(toQuestionForm(result.data))
      }
      setQuestionNotice({
        kind: 'info',
        title: isEdit ? '题目已更新' : '题目已创建',
        message: result.data ? `题目 #${result.data.id} 已保存。` : '后端没有返回题目详情。',
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

  function buildQuestionPayload(form: QuestionFormState) {
    const tagCodes = parseCodeList(form.tagCodes)
    const standardAnswer = form.standardAnswer.trim()
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
        message: '中文原文、语境、语法点、标签 code 和标准答案都必须填写。',
      })
      return null
    }

    return {
      questionType: 'TRANSLATION_ZH_TO_JA',
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
          answerType: 'STANDARD',
          primaryAnswer: true,
          sortOrder: 0,
        },
        ...referenceAnswers.map((answer, index) => ({
          answerText: answer,
          answerType: 'REFERENCE',
          primaryAnswer: false,
          sortOrder: index + 1,
        })),
      ],
    }
  }

  async function handleToggleQuestionEnabled(question: Question) {
    setQuestionActionId(question.id)
    setQuestionNotice(null)

    try {
      const response = await fetch(`/api/questions/${question.id}/enabled`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ enabled: !question.enabled }),
      })
      await readApiResponse<null>(response)
      setQuestionNotice({
        kind: 'info',
        title: question.enabled ? '题目已停用' : '题目已启用',
        message: `题目 #${question.id} 状态已更新。`,
      })
      if (selectedManagedQuestion?.id === question.id) {
        setSelectedManagedQuestion({ ...selectedManagedQuestion, enabled: !question.enabled })
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
      const response = await fetch(`/api/questions/${question.id}`, {
        method: 'DELETE',
      })
      await readApiResponse<null>(response)
      setQuestionNotice({
        kind: 'info',
        title: '题目已删除',
        message: `题目 #${question.id} 已逻辑删除。`,
      })
      if (selectedManagedQuestion?.id === question.id) {
        setSelectedManagedQuestion(null)
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
    <main className="app-shell">
      <section className="workspace">
        <header className="app-header">
          <div className="title-group">
            <p className="eyebrow">Japanese Translation Practice</p>
            <h1>日语翻译练习</h1>
          </div>

          <nav className="top-nav" aria-label="主导航">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.key}
                type="button"
                className={activePage === item.key ? 'nav-button is-active' : 'nav-button'}
                aria-current={activePage === item.key ? 'page' : undefined}
                onClick={() => setActivePage(item.key)}
              >
                {item.label}
              </button>
            ))}
          </nav>
        </header>

        {activePage === 'practice' ? (
          <section className="page-content" aria-label="practice page">
            <header className="page-heading">
              <div>
                <p className="eyebrow">首页</p>
                <h2>问题生成与回答</h2>
              </div>
              <StatusBadge label="后端服务" value={backendStatus} />
            </header>

            <div className="practice-grid">
              <section className="surface generator-panel" aria-label="question generator">
                <div className="section-title">
                  <span className="label">生成条件</span>
                  <strong>中译日题目</strong>
                </div>

                <form className="form-grid" onSubmit={(event) => event.preventDefault()}>
                  <label>
                    <span>题目数量</span>
                    <select value={questionCount} onChange={(event) => setQuestionCount(event.target.value)}>
                      <option value="1">1</option>
                      <option value="2">2</option>
                      <option value="3">3</option>
                      <option value="4">4</option>
                      <option value="5">5</option>
                    </select>
                  </label>

                  <label>
                    <span>JLPT 等级</span>
                    <select value={level} onChange={(event) => setLevel(event.target.value)}>
                      <option value="">默认 N3</option>
                      <option value="N5">N5</option>
                      <option value="N4">N4</option>
                      <option value="N3">N3</option>
                      <option value="N2">N2</option>
                      <option value="N1">N1</option>
                    </select>
                  </label>

                  <label>
                    <span>难度</span>
                    <select value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>
                      <option value="1">1 - 入门</option>
                      <option value="2">2 - 简单</option>
                      <option value="3">3 - 标准</option>
                      <option value="4">4 - 较难</option>
                      <option value="5">5 - 挑战</option>
                    </select>
                  </label>

                  <label>
                    <span>场景标签</span>
                    <select value={sceneTagCode} onChange={(event) => setSceneTagCode(event.target.value)}>
                      <option value="">{practiceTagsLoading ? '标签加载中' : '不限制场景'}</option>
                      {sceneTags.map((tag) => (
                        <option key={tag.id} value={tag.code}>
                          {tag.name}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label>
                    <span>功能标签</span>
                    <select value={functionTagCode} onChange={(event) => setFunctionTagCode(event.target.value)}>
                      <option value="">{practiceTagsLoading ? '标签加载中' : '不限制功能'}</option>
                      {functionTags.map((tag) => (
                        <option key={tag.id} value={tag.code}>
                          {tag.name}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="wide-field">
                    <span>额外要求</span>
                    <input
                      value={extraRequirements}
                      maxLength={500}
                      placeholder="例如：偏口语、商务场景、考试表达"
                      onChange={(event) => setExtraRequirements(event.target.value)}
                    />
                  </label>

                  <button
                    type="button"
                    className="primary-button"
                    disabled={questionGenerating}
                    onClick={handleGenerateQuestion}
                  >
                    {questionGenerating ? '生成中' : '生成题目'}
                  </button>
                </form>

                {practiceTagsError ? <div className="error-message">标签加载失败：{practiceTagsError}</div> : null}
              </section>

              <section className="surface question-panel" aria-label="question preview">
                <div className="section-title">
                  <span className="label">题目展示</span>
                  <strong>{selectedQuestion ? `题目 #${selectedQuestion.id}` : '等待生成题目'}</strong>
                </div>

                {generatedQuestions.length > 1 ? (
                  <div className="question-selector" aria-label="generated questions">
                    {generatedQuestions.map((question, index) => (
                      <button
                        key={question.id}
                        type="button"
                        className={selectedQuestionIndex === index ? 'is-selected' : ''}
                        onClick={() => handleSelectQuestion(index)}
                      >
                        {index + 1}
                      </button>
                    ))}
                  </div>
                ) : null}

                <dl className="question-details">
                  <div>
                    <dt>中文原文</dt>
                    <dd>{selectedQuestion?.sourceText ?? '待生成'}</dd>
                  </div>
                  <div>
                    <dt>语境</dt>
                    <dd>{selectedQuestion?.contextText ?? '待后端返回'}</dd>
                  </div>
                  <div>
                    <dt>语法点</dt>
                    <dd>{selectedQuestion?.grammarPoint ?? '待后端返回'}</dd>
                  </div>
                  <div>
                    <dt>标签</dt>
                    <dd>
                      {selectedQuestion ? (
                        <span className="tag-chip-row">
                          {selectedQuestion.tags.map((tag) => (
                            <span key={tag.id}>{tag.name}</span>
                          ))}
                        </span>
                      ) : (
                        '待后端返回'
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt>难度</dt>
                    <dd>
                      {selectedQuestion
                        ? `${selectedQuestion.level} / ${selectedQuestion.difficulty}`
                        : '待后端返回'}
                    </dd>
                  </div>
                  <div>
                    <dt>来源</dt>
                    <dd>{selectedQuestion ? selectedQuestion.sourceType : '待后端返回'}</dd>
                  </div>
                </dl>
              </section>

              <section className="surface answer-panel" aria-label="answer input">
                <div className="section-title">
                  <span className="label">回答</span>
                  <strong>输入日语译文</strong>
                </div>

                <textarea
                  value={answerText}
                  disabled={!selectedQuestion}
                  placeholder={selectedQuestion ? '输入你的日语回答。' : '题目生成后，在这里输入你的日语回答。'}
                  onChange={(event) => setAnswerText(event.target.value)}
                />

                <div className="action-row">
                  <button
                    type="button"
                    className="primary-button"
                    disabled={!selectedQuestion || answerScoring}
                    onClick={handleSubmitAnswer}
                  >
                    {answerScoring ? '评分中' : '提交答案'}
                  </button>
                  <button type="button" disabled={!selectedQuestion && !answerText} onClick={handleClearAnswer}>
                    清空
                  </button>
                </div>
              </section>

              <section className="surface result-panel" aria-label="answer result">
                <div className="section-title">
                  <span className="label">结果</span>
                  <strong>评分状态</strong>
                </div>

                {practiceNotice ? (
                  <div className={practiceNotice.kind === 'error' ? 'notice is-error' : 'notice'}>
                    <strong>{practiceNotice.title}</strong>
                    <p>{practiceNotice.message}</p>
                  </div>
                ) : (
                  <div className="notice">
                    <strong>等待生成</strong>
                    <p>生成题目后这里会显示接口状态和标准答案。</p>
                  </div>
                )}

                {answerReview ? (
                  <div className="review-result">
                    <div className="score-summary">
                      <span>总分</span>
                      <strong>{formatScore(answerReview.totalScore)}</strong>
                    </div>

                    <dl className="score-grid">
                      <div>
                        <dt>语法与词汇</dt>
                        <dd>{answerReview.scores.grammarVocabularyScore}</dd>
                      </div>
                      <div>
                        <dt>自然度与流畅度</dt>
                        <dd>{answerReview.scores.naturalFluencyScore}</dd>
                      </div>
                      <div>
                        <dt>敬语与场景</dt>
                        <dd>{answerReview.scores.scenarioAdaptationScore}</dd>
                      </div>
                      <div>
                        <dt>表达完整性</dt>
                        <dd>{answerReview.scores.informationCompletenessScore}</dd>
                      </div>
                    </dl>

                    <section className="review-section">
                      <strong>总评</strong>
                      <p>{answerReview.overallComment}</p>
                    </section>

                    <dl className="comment-list">
                      <div>
                        <dt>语法评价</dt>
                        <dd>{answerReview.comments.grammarComment}</dd>
                      </div>
                      <div>
                        <dt>词汇评价</dt>
                        <dd>{answerReview.comments.vocabularyComment}</dd>
                      </div>
                      <div>
                        <dt>自然度评价</dt>
                        <dd>{answerReview.comments.naturalnessComment}</dd>
                      </div>
                      <div>
                        <dt>场景适合度评价</dt>
                        <dd>{answerReview.comments.scenarioComment}</dd>
                      </div>
                    </dl>

                    <ReviewList title="错误分析" emptyText="本次没有返回具体错误。" items={answerReview.errorAnalysis}>
                      {(item) => (
                        <div>
                          <span>{item.type} / {item.severity}</span>
                          <strong>{item.original}</strong>
                          <p>{item.issue}</p>
                          <p>{item.suggestion}</p>
                        </div>
                      )}
                    </ReviewList>

                    <ReviewList title="修改建议" emptyText="本次没有返回修改建议。" items={answerReview.revisionSuggestions}>
                      {(item) => <p>{item}</p>}
                    </ReviewList>

                    <ReviewList
                      title="推荐表达"
                      emptyText="本次没有返回推荐表达。"
                      items={answerReview.recommendedExpressions}
                    >
                      {(item) => (
                        <div>
                          <span>{item.formality}</span>
                          <strong>{item.expression}</strong>
                          <p>{item.usage}</p>
                          <p>{item.note}</p>
                        </div>
                      )}
                    </ReviewList>
                  </div>
                ) : null}

                {selectedQuestion ? (
                  <details className="answer-reference">
                    <summary>查看标准答案</summary>
                    <ol>
                      {selectedQuestion.answers.map((answer) => (
                        <li key={answer.id}>
                          <span>
                            {answer.answerType === 'STANDARD' ? '标准' : '参考'}
                            {answer.primaryAnswer ? ' / 主答案' : ''}
                          </span>
                          <strong>{answer.answerText}</strong>
                        </li>
                      ))}
                    </ol>
                  </details>
                ) : null}
              </section>
            </div>
          </section>
        ) : null}

        {activePage === 'tags' ? (
          <section className="page-content" aria-label="tag management page">
            <header className="page-heading">
              <div>
                <p className="eyebrow">管理</p>
                <h2>标签管理</h2>
              </div>
              <StatusBadge label="后端服务" value={backendStatus} />
            </header>

            <div className="status-panel">
              <StatusBlock label="后端服务" value={backendStatus} />
              <StatusBlock label="响应状态" value={healthError ?? health?.message ?? '等待响应'} />
              <StatusBlock label="当前结果" value={tagLoading ? '加载中' : `${firstItemNo}-${lastItemNo} / ${total}`} />
              <StatusBlock label="本页类型" value={`场景 ${sceneCount} / 功能 ${functionCount}`} />
            </div>

            <section className="surface tag-panel" aria-label="tag query">
              <form className="filter-bar" onSubmit={(event) => event.preventDefault()}>
                <label>
                  <span>标签类型</span>
                  <select value={tagType} onChange={(event) => setTagType(event.target.value as TagFilter)}>
                    <option value="">全部</option>
                    <option value="SCENE">场景</option>
                    <option value="FUNCTION">功能</option>
                  </select>
                </label>

                <label>
                  <span>父级 ID</span>
                  <input
                    inputMode="numeric"
                    pattern="[0-9]*"
                    placeholder="不限制"
                    value={parentId}
                    onChange={(event) => setParentId(event.target.value.replace(/\D/g, ''))}
                  />
                </label>

                <label className="checkbox-field">
                  <input
                    type="checkbox"
                    checked={enabledOnly}
                    onChange={(event) => setEnabledOnly(event.target.checked)}
                  />
                  <span>仅启用</span>
                </label>

                <label>
                  <span>每页数量</span>
                  <select value={size} onChange={(event) => setSize(Number(event.target.value))}>
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                  </select>
                </label>
              </form>

              {tagError ? <div className="error-message">{tagError}</div> : null}

              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>类型</th>
                      <th>父级</th>
                      <th>编码</th>
                      <th>名称</th>
                      <th>说明</th>
                      <th>排序</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tags.map((tag) => (
                      <tr key={tag.id}>
                        <td>{tag.id}</td>
                        <td>{tag.tagType === 'SCENE' ? '场景' : '功能'}</td>
                        <td>{tag.parentId ?? '-'}</td>
                        <td className="code-cell">{tag.code}</td>
                        <td>{tag.name}</td>
                        <td>{tag.description ?? '-'}</td>
                        <td>{tag.sortOrder}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {!tagLoading && tags.length === 0 ? <p className="empty-state">暂无标签数据</p> : null}
              </div>

              <div className="pagination-bar">
                <span>
                  第 {page} / {totalPages} 页
                </span>
                <div className="pagination-actions">
                  <button
                    type="button"
                    disabled={page <= 1 || tagLoading}
                    onClick={() => setPage((value) => value - 1)}
                  >
                    上一页
                  </button>
                  <button
                    type="button"
                    disabled={page >= totalPages || tagLoading}
                    onClick={() => setPage((value) => value + 1)}
                  >
                    下一页
                  </button>
                </div>
              </div>
            </section>
          </section>
        ) : null}

        {activePage === 'questions' ? (
          <section className="page-content" aria-label="question management page">
            <header className="page-heading">
              <div>
                <p className="eyebrow">管理</p>
                <h2>问题管理</h2>
              </div>
              <StatusBadge label="后端服务" value={backendStatus} />
            </header>

            <div className="status-panel">
              <StatusBlock label="后端服务" value={backendStatus} />
              <StatusBlock label="当前结果" value={questionLoading ? '加载中' : `${questionFirstItemNo}-${questionLastItemNo} / ${questionTotal}`} />
              <StatusBlock label="本页启用" value={`${enabledQuestionCount} 道`} />
              <StatusBlock label="本页人工题" value={`${manualQuestionCount} 道`} />
            </div>

            <section className="surface question-management-panel" aria-label="question query">
              <form className="question-filter-bar" onSubmit={(event) => event.preventDefault()}>
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

                <label className="wide-field">
                  <span>标签 code</span>
                  <input
                    value={questionFilters.tagCodes}
                    placeholder="多个 code 用逗号分隔"
                    onChange={(event) => updateQuestionFilters({ tagCodes: event.target.value })}
                  />
                </label>

                <label>
                  <span>每页数量</span>
                  <select value={questionFilters.size} onChange={(event) => updateQuestionFilters({ size: Number(event.target.value) })}>
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                  </select>
                </label>
              </form>

              {questionError ? <div className="error-message">{questionError}</div> : null}
              {questionNotice ? (
                <div className={questionNotice.kind === 'error' ? 'notice is-error' : 'notice'}>
                  <strong>{questionNotice.title}</strong>
                  <p>{questionNotice.message}</p>
                </div>
              ) : null}

              <div className="table-wrap">
                <table className="question-table">
                  <thead>
                    <tr>
                      <th>ID</th>
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
                        <td>{question.id}</td>
                        <td>{question.sourceText}</td>
                        <td>{question.level} / {question.difficulty}</td>
                        <td>{question.sourceType === 'AI' ? 'AI' : '人工'}</td>
                        <td>{question.enabled ? '启用' : '停用'}</td>
                        <td>
                          <span className="tag-chip-row">
                            {question.tags.slice(0, 3).map((tag) => (
                              <span key={tag.id}>{tag.name}</span>
                            ))}
                            {question.tags.length > 3 ? <span>+{question.tags.length - 3}</span> : null}
                          </span>
                        </td>
                        <td>
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
                <span>
                  第 {questionFilters.page} / {questionTotalPages} 页
                </span>
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
                    新建题目
                  </button>
                </div>
              </div>
            </section>

            <div className="question-management-grid">
              <section className="surface question-detail-panel" aria-label="question detail">
                <div className="section-title">
                  <span className="label">详情</span>
                  <strong>{selectedManagedQuestion ? `题目 #${selectedManagedQuestion.id}` : '未选择题目'}</strong>
                </div>

                {selectedManagedQuestion ? (
                  <dl className="question-details">
                    <div>
                      <dt>中文原文</dt>
                      <dd>{selectedManagedQuestion.sourceText}</dd>
                    </div>
                    <div>
                      <dt>语境</dt>
                      <dd>{selectedManagedQuestion.contextText}</dd>
                    </div>
                    <div>
                      <dt>语法点</dt>
                      <dd>{selectedManagedQuestion.grammarPoint}</dd>
                    </div>
                    <div>
                      <dt>属性</dt>
                      <dd>{formatQuestionFlags(selectedManagedQuestion)}</dd>
                    </div>
                    <div>
                      <dt>标签</dt>
                      <dd>
                        <span className="tag-chip-row">
                          {selectedManagedQuestion.tags.map((tag) => (
                            <span key={tag.id}>{tag.name} / {tag.code}</span>
                          ))}
                        </span>
                      </dd>
                    </div>
                    <div>
                      <dt>答案</dt>
                      <dd>
                        <ol className="compact-answer-list">
                          {selectedManagedQuestion.answers.map((answer) => (
                            <li key={answer.id}>
                              <span>{answer.answerType === 'STANDARD' ? '标准' : '参考'}</span>
                              <strong>{answer.answerText}</strong>
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

              <section className="surface question-form-panel" aria-label="question form">
                <div className="section-title">
                  <span className="label">{questionFormMode === 'edit' ? '整体更新' : '人工录入'}</span>
                  <strong>{questionFormMode === 'edit' && selectedManagedQuestion ? `编辑题目 #${selectedManagedQuestion.id}` : '新建题目'}</strong>
                </div>

                <form className="question-edit-form" onSubmit={(event) => event.preventDefault()}>
                  <label className="wide-field">
                    <span>中文原文</span>
                    <textarea
                      value={questionForm.sourceText}
                      placeholder="输入中文句子"
                      onChange={(event) => updateQuestionForm({ sourceText: event.target.value })}
                    />
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
                    <span>语法点</span>
                    <input
                      value={questionForm.grammarPoint}
                      placeholder="例如：予定を表す表現"
                      onChange={(event) => updateQuestionForm({ grammarPoint: event.target.value })}
                    />
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

                  <label className="wide-field">
                    <span>标准答案</span>
                    <textarea
                      value={questionForm.standardAnswer}
                      placeholder="输入主标准答案"
                      onChange={(event) => updateQuestionForm({ standardAnswer: event.target.value })}
                    />
                  </label>

                  <label className="wide-field">
                    <span>参考答案</span>
                    <textarea
                      value={questionForm.referenceAnswers}
                      placeholder="可选，每行一个参考答案"
                      onChange={(event) => updateQuestionForm({ referenceAnswers: event.target.value })}
                    />
                  </label>

                  <div className="action-row wide-field">
                    <button type="button" className="primary-button" disabled={questionSaving} onClick={handleSaveQuestion}>
                      {questionSaving ? '保存中' : questionFormMode === 'edit' ? '保存修改' : '创建题目'}
                    </button>
                    <button type="button" onClick={handleStartCreateQuestion}>
                      清空表单
                    </button>
                  </div>
                </form>
              </section>
            </div>
          </section>
        ) : null}

        {activePage === 'reviews' ? (
          <ManagementPlaceholder
            eyebrow="复习"
            title="错题复习"
            description="错题记录、复习计划和再练习流程将在答题评分链路完成后接入。"
            items={['错题列表', '复习计划', '再练习入口', '学习记录']}
          />
        ) : null}
      </section>
    </main>
  )
}

function StatusBadge({ label, value }: { label: string; value: string }) {
  return (
    <div className="status-badge">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function ReviewList<T>({
  title,
  emptyText,
  items,
  children,
}: {
  title: string
  emptyText: string
  items: T[]
  children: (item: T) => ReactNode
}) {
  return (
    <section className="review-section">
      <strong>{title}</strong>
      {items.length > 0 ? (
        <ol className="review-list">
          {items.map((item, index) => (
            <li key={index}>{children(item)}</li>
          ))}
        </ol>
      ) : (
        <p>{emptyText}</p>
      )}
    </section>
  )
}

function formatScore(score: number) {
  return score.toFixed(2)
}

function parseCodeList(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
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

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败'
}

async function readApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  let result: ApiResponse<T> | null = null

  try {
    result = (await response.json()) as ApiResponse<T>
  } catch {
    throw new Error(response.ok ? '响应不是合法 JSON' : `HTTP ${response.status}`)
  }

  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `HTTP ${response.status}`)
  }

  return result
}

function StatusBlock({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="label">{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function ManagementPlaceholder({
  eyebrow,
  title,
  description,
  items,
}: {
  eyebrow: string
  title: string
  description: string
  items: string[]
}) {
  return (
    <section className="page-content" aria-label={title}>
      <header className="page-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
        </div>
      </header>

      <section className="surface placeholder-panel">
        <div>
          <span className="label">当前状态</span>
          <strong>入口已预留，功能待接入</strong>
          <p>{description}</p>
        </div>

        <div className="capability-list" aria-label={`${title} planned capabilities`}>
          {items.map((item) => (
            <span key={item}>{item}</span>
          ))}
        </div>
      </section>
    </section>
  )
}

export default App
