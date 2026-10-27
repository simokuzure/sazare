import { useCallback, useEffect, useRef, useState } from 'react'
import { analyzeChatImport, ChatImportRequestError, confirmChatImport } from '../../api/chatImportApi'
import { getErrorMessage } from '../../api/client'
import { useLanguage } from '../../i18n/LanguageContext'
import type { PracticeNotice } from '../../types/api'
import type { ChatImportDraft, LeaveGuardListener } from '../../types/chatImport'
import type { ErrorType } from '../../types/errorType'
import type { ReviewCard } from '../../types/review'
import { createImportDraft, MAX_CHAT_FILE_BYTES, toImportItem, validateImportDraft, type DraftErrors } from './chatImportModel'
import { loadImportCards, loadImportTypes } from './chatImportMetadata'

function aborted(error: unknown) { return error instanceof DOMException && error.name === 'AbortError' }

export function useChatImport(onLeaveGuardChange: LeaveGuardListener, onSaved: () => void) {
  const { learningMode, text } = useLanguage()
  const message = useCallback((error: unknown) => getErrorMessage(error, learningMode), [learningMode])
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState('')
  const [reading, setReading] = useState(false)
  const [analyzing, setAnalyzing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [hasAnalysis, setHasAnalysis] = useState(false)
  const [drafts, setDrafts] = useState<ChatImportDraft[]>([])
  const [activeKey, setActiveKey] = useState<number | null>(null)
  const [errors, setErrors] = useState<Record<number, DraftErrors>>({})
  const [notice, setNotice] = useState<PracticeNotice | null>(null)
  const [validationFailed, setValidationFailed] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [types, setTypes] = useState<ErrorType[]>([])
  const [cards, setCards] = useState<ReviewCard[]>([])
  const [typesLoading, setTypesLoading] = useState(true)
  const [cardsLoading, setCardsLoading] = useState(true)
  const [typesError, setTypesError] = useState<string | null>(null)
  const [cardsError, setCardsError] = useState<string | null>(null)
  const [typesRevision, setTypesRevision] = useState(0)
  const [cardsRevision, setCardsRevision] = useState(0)
  const analysisController = useRef<AbortController | null>(null)
  const fileRevision = useRef(0)
  const confirmingRef = useRef(false)
  const analyzingRef = useRef(false)
  const mounted = useRef(true)
  const unsaved = Boolean(file && (!hasAnalysis || drafts.some(draft => !draft.saved)))
  const unsavedRef = useRef(unsaved)
  unsavedRef.current = unsaved

  const mayLeave = useCallback(() => {
    if (confirmingRef.current) return false
    return !unsavedRef.current || window.confirm(text(
      '离开会丢失当前文件及尚未确认的候选，已保存的卡片不受影响。确认离开？',
      'Leaving discards the file and unconfirmed candidates. Saved cards are retained. Leave?'))
  }, [text])

  useEffect(() => {
    onLeaveGuardChange(mayLeave)
    return () => onLeaveGuardChange(null)
  }, [mayLeave, onLeaveGuardChange])

  useEffect(() => {
    mounted.current = true
    function beforeUnload(event: BeforeUnloadEvent) {
      if (unsavedRef.current || confirmingRef.current) { event.preventDefault(); event.returnValue = '' }
    }
    window.addEventListener('beforeunload', beforeUnload)
    return () => {
      mounted.current = false
      analysisController.current?.abort()
      window.removeEventListener('beforeunload', beforeUnload)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setTypesLoading(true)
    setTypesError(null)
    loadImportTypes(controller.signal).then(value => {
      if (!controller.signal.aborted) setTypes(value)
    }).catch(error => { if (!aborted(error)) setTypesError(message(error)) })
      .finally(() => { if (!controller.signal.aborted) setTypesLoading(false) })
    return () => controller.abort()
  }, [typesRevision, message])

  useEffect(() => {
    const controller = new AbortController()
    setCardsLoading(true)
    setCardsError(null)
    loadImportCards(learningMode, controller.signal).then(value => {
      if (!controller.signal.aborted) setCards(value)
    }).catch(error => { if (!aborted(error)) setCardsError(message(error)) })
      .finally(() => { if (!controller.signal.aborted) setCardsLoading(false) })
    return () => controller.abort()
  }, [learningMode, cardsRevision, message])

  async function selectFiles(files: File[]) {
    if (confirmingRef.current) return
    if (files.length !== 1 || !/\.md$/i.test(files[0].name)) {
      setNotice({ kind: 'error', title: text('文件不符合要求', 'Invalid file'), message: text('请选择单个 .md 文件。', 'Choose one .md file.') })
      return
    }
    const next = files[0]
    if (!next.size || next.size > MAX_CHAT_FILE_BYTES) {
      setNotice({ kind: 'error', title: text('文件不符合要求', 'Invalid file'), message: text('文件不能为空，且不能超过 128 KiB。', 'The file must be non-empty and at most 128 KiB.') })
      return
    }
    if (!mayLeave()) return
    analysisController.current?.abort()
    analyzingRef.current = false
    const revision = ++fileRevision.current
    setAnalyzing(false)
    setFile(next)
    setPreview('')
    setReading(true)
    setHasAnalysis(false)
    setDrafts([])
    setActiveKey(null)
    setErrors({})
    setNotice(null)
    setUncertain(false)
    try {
      const decoded = new TextDecoder('utf-8', { fatal: true }).decode(await next.arrayBuffer())
      if (revision === fileRevision.current && mounted.current) setPreview(decoded)
    } catch {
      if (revision === fileRevision.current && mounted.current) {
        setFile(null)
        setNotice({ kind: 'error', title: text('无法读取文件', 'Could not read file'), message: text('请使用 UTF-8 编码的 Markdown 文件。', 'Use a UTF-8 encoded Markdown file.') })
      }
    } finally {
      if (revision === fileRevision.current && mounted.current) setReading(false)
    }
  }

  async function analyze() {
    if (!file || reading || analyzingRef.current || confirmingRef.current) return
    if (hasAnalysis && !mayLeave()) return
    const controller = new AbortController()
    analysisController.current?.abort()
    analysisController.current = controller
    analyzingRef.current = true
    setAnalyzing(true)
    setNotice(null)
    setUncertain(false)
    setDrafts([])
    setErrors({})
    setHasAnalysis(false)
    setActiveKey(null)
    try {
      const result = await analyzeChatImport(file, learningMode, controller.signal)
      if (!controller.signal.aborted && mounted.current) {
        setDrafts(result.candidates.map(createImportDraft))
        setActiveKey(result.candidates.length ? 0 : null)
        setHasAnalysis(true)
      }
    } catch (error) {
      if (!aborted(error) && !controller.signal.aborted && mounted.current) {
        setNotice({ kind: 'error', title: text('分析失败', 'Analysis failed'), message: message(error) })
      }
    } finally {
      if (!controller.signal.aborted && mounted.current) {
        analyzingRef.current = false
        setAnalyzing(false)
      }
    }
  }

  function updateDraft(key: number, patch: Partial<ChatImportDraft>) {
    if (confirmingRef.current) return
    if (validationFailed) { setNotice(null); setValidationFailed(false) }
    setDrafts(current => current.map(draft => draft.key === key && !draft.saved ? { ...draft, ...patch } : draft))
    setErrors(current => ({ ...current, [key]: {} }))
  }

  function checkDraft(key: number) {
    const draft = drafts.find(item => item.key === key)
    if (draft && !draft.saved) setErrors(current => ({ ...current, [key]: validateImportDraft(draft, learningMode, types, cards) }))
  }

  async function confirmSelected() {
    if (confirmingRef.current || typesLoading || cardsLoading || typesError || cardsError) return
    const selected = drafts.filter(draft => draft.selected && !draft.saved)
    if (!selected.length) return
    const nextErrors: Record<number, DraftErrors> = {}
    for (const draft of selected) nextErrors[draft.key] = validateImportDraft(draft, learningMode, types, cards)
    setErrors(nextErrors)
    const invalid = selected.find(draft => Object.keys(nextErrors[draft.key]).length)
    if (invalid) {
      setValidationFailed(true)
      setActiveKey(invalid.key)
      setNotice({ kind: 'error', title: text('请检查候选内容', 'Check the selected candidates'), message: text('已定位到第一个需要修改的候选。', 'The first invalid candidate is open for editing.') })
      return
    }
    if (uncertain && !window.confirm(text('上次提交结果未知。再次提交可能重复增加失败次数，仍要提交？',
      'The previous result is unknown. Submitting again may add another failure. Submit anyway?'))) return
    confirmingRef.current = true
    setValidationFailed(false)
    setConfirming(true)
    setNotice(null)
    try {
      await confirmChatImport({ learningMode, items: selected.map(toImportItem) })
      if (!mounted.current) return
      const saved = new Set(selected.map(draft => draft.key))
      setDrafts(current => current.map(draft => saved.has(draft.key) ? { ...draft, selected: false, saved: true } : draft))
      setUncertain(false)
      onSaved()
      setCardsRevision(value => value + 1)
    } catch (error) {
      if (!mounted.current) return
      const unknown = !(error instanceof ChatImportRequestError) || error.uncertain
      setUncertain(unknown)
      setNotice({ kind: 'error', title: unknown ? text('提交结果未知', 'Submission result unknown') : text('确认失败', 'Confirmation failed'),
        message: unknown ? text('请先检查复习卡片，再决定是否重新提交。重复提交可能再次增加失败次数。',
          'Check the review cards before deciding to submit again. A repeated submission may add another failure.') : message(error) })
    } finally {
      confirmingRef.current = false
      if (mounted.current) setConfirming(false)
    }
  }

  return { file, preview, reading, analyzing, confirming, hasAnalysis, drafts, activeKey, setActiveKey, errors, notice, setNotice,
    uncertain, types, cards, typesLoading, cardsLoading, typesError, cardsError, mayLeave, selectFiles, analyze,
    updateDraft, checkDraft, confirmSelected, reloadTypes: () => setTypesRevision(value => value + 1),
    reloadCards: () => setCardsRevision(value => value + 1) }
}
