import { useRef, useState, type ReactNode, type RefObject } from 'react'
import { useLanguage } from '../../i18n/LanguageContext'

export default function ChatImportUpload({ file, reading, analyzing, confirming, hasAnalysis, inputRef, onSelectFiles, onAnalyze, onShowPrompt, summary, confirmAction }: {
  file: File | null; reading: boolean; analyzing: boolean; confirming: boolean; hasAnalysis: boolean
  inputRef: RefObject<HTMLInputElement | null>
  onSelectFiles: (files: File[]) => Promise<void>; onAnalyze: () => Promise<void>; onShowPrompt: () => void
  summary?: string; confirmAction?: ReactNode
}) {
  const { learningMode, text } = useLanguage()
  const [dragging, setDragging] = useState(false)
  const menu = useRef<HTMLDetailsElement>(null)
  const busy = reading || analyzing || confirming
  const fileInfo = file ? `${(file.size / 1024).toFixed(1)} KiB · ${learningMode === 'ZH_TO_JA' ? '中 → 日' : 'EN → JP'}` : ''
  const chooseFile = () => inputRef.current?.click()
  const analyzeLabel = analyzing ? text('正在分析…', 'Analyzing…') : hasAnalysis ? text('重新分析', 'Analyze again') : text('开始分析', 'Analyze conversation')
  const closeMenu = () => {
    if (menu.current) { menu.current.open = false; menu.current.querySelector('summary')?.focus() }
  }
  const runFileAction = (action: () => void) => { closeMenu(); action() }

  return <section className={`surface import-file-panel${hasAnalysis ? '' : ' import-upload-panel'}${dragging ? ' is-dragging' : ''}`}
    aria-label={text('上传聊天文件', 'Upload conversation')}
    onDragOver={event => { event.preventDefault(); if (!busy) setDragging(true) }} onDragLeave={() => setDragging(false)}
    onDrop={event => { event.preventDefault(); setDragging(false); if (!confirming) void onSelectFiles(Array.from(event.dataTransfer.files)) }}>
    <input ref={inputRef} type="file" accept=".md" hidden aria-label={text('选择 Markdown 文件', 'Choose Markdown file')} disabled={confirming}
      onChange={event => { const files = Array.from(event.target.files ?? []); event.target.value = ''; if (files.length) void onSelectFiles(files) }} />
    {hasAnalysis ? <>
      <div className="import-file-info"><span className="import-file-icon" aria-hidden="true">MD</span>
        <div className="import-file-copy"><strong>{file?.name}</strong><div className="import-file-meta"><span>{fileInfo}</span><span className="import-file-status">{text('✓ 分析完成', '✓ Analyzed')}</span></div></div></div>
      <div className="import-file-actions">
        {summary ? <span className="import-summary" role="status" aria-live="polite">{summary}</span> : null}
        <details className="import-file-menu" ref={menu} onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.open = false }}
          onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); closeMenu() } }}>
          <summary>{text('文件操作', 'File actions')}<span aria-hidden="true">⌄</span></summary>
          <div className="import-file-menu-items">
            <button type="button" onClick={() => runFileAction(onShowPrompt)}>{text('导出提示词', 'Export prompt')}</button>
            <button type="button" disabled={confirming} onClick={() => runFileAction(chooseFile)}>{text('更换文件', 'Change file')}</button>
            <button type="button" disabled={busy} onClick={() => runFileAction(() => { void onAnalyze() })}>{analyzeLabel}</button>
          </div>
        </details>
        {confirmAction}
      </div>
    </> : <>
      <div className="import-upload-heading">
        <h3>{text('导入一段日语练习聊天', 'Import a Japanese practice conversation')}</h3>
        <p>{text('AI 分析后，你可以逐项检查、编辑并加入复习。', 'After AI analysis, check and edit each item before adding it to your review.')}</p></div>
      <div className="import-upload-layout">
        <div className="import-upload-dropzone">
          <span className="import-upload-symbol" aria-hidden="true"><svg viewBox="0 0 48 48" fill="none"><path d="M28 6H13a3 3 0 0 0-3 3v30a3 3 0 0 0 3 3h22a3 3 0 0 0 3-3V16L28 6Z" /><path d="M28 6v10h10M24 34V22m-5 5 5-5 5 5" /></svg></span>
          <h4>{file?.name ?? text('选择或拖入聊天文件', 'Choose or drop a conversation file')}</h4>
          <p>{file ? fileInfo : text('支持 UTF-8 编码的 Markdown（.md）文件', 'Supports UTF-8 Markdown (.md) files')}</p>
          {file ? <span className="import-upload-ready">{reading ? text('正在读取文件…', 'Reading file…') : analyzing ? text('AI 正在分析', 'AI is analyzing') : text('文件已准备好', 'File ready')}</span> : null}
          <div className="import-upload-actions"><button type="button" className={file ? undefined : 'primary-button'} disabled={confirming} onClick={chooseFile}>{file ? text('更换文件', 'Change file') : text('选择文件', 'Choose file')}</button>
            {file ? <button type="button" className="primary-button" disabled={busy} onClick={() => void onAnalyze()}>{analyzeLabel}</button> : null}</div>
          <div className="import-upload-limits"><span>{text('文件最多 128 KiB', 'Up to 128 KiB')}</span><span>{text('正文最多 30,000 字符', 'Up to 30,000 body characters')}</span></div>
        </div>
        <aside className="import-upload-guide"><h4>{text('还没有聊天文件？', 'Need a conversation file?')}</h4>
          <p>{text('在原聊天中使用导出提示词，将对话保存为 .md 文件。保留角色标题和消息顺序。', 'Use the export prompt in your conversation and save it as an .md file. Keep the role headings and message order.')}</p>
          <button type="button" onClick={onShowPrompt}>{text('导出提示词', 'Export prompt')}</button>
          <div className="import-upload-example"><span>{text('文件格式示例', 'File format example')}</span><pre><strong>## User</strong>{'\n日本語を勉強しています。\n\n'}<strong>## Assistant</strong>{'\nどのくらい勉強していますか？'}</pre></div>
        </aside>
      </div>
      <p className="import-upload-note">{text('分析结果由你确认后加入复习，上传文件不会自动保存为卡片。', 'Review cards are saved only after you check and confirm the analysis.')}</p>
    </>}
  </section>
}
