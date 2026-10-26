import { useState, type RefObject } from 'react'
import { useLanguage } from '../../i18n/LanguageContext'

export default function ChatImportUpload({ file, reading, analyzing, confirming, hasAnalysis, inputRef, onSelectFiles, onAnalyze, onShowPrompt }: {
  file: File | null; reading: boolean; analyzing: boolean; confirming: boolean; hasAnalysis: boolean
  inputRef: RefObject<HTMLInputElement | null>
  onSelectFiles: (files: File[]) => Promise<void>; onAnalyze: () => Promise<void>; onShowPrompt: () => void
}) {
  const { learningMode, text } = useLanguage()
  const [dragging, setDragging] = useState(false)
  const busy = reading || analyzing || confirming
  const fileInfo = file ? `${(file.size / 1024).toFixed(1)} KiB · ${learningMode === 'ZH_TO_JA' ? '中 → 日' : 'EN → JP'}` : ''
  const chooseFile = () => inputRef.current?.click()
  const analyzeLabel = analyzing ? text('正在分析…', 'Analyzing…') : hasAnalysis ? text('重新分析', 'Analyze again') : text('开始分析', 'Analyze conversation')

  return <section className={`surface import-file-panel${hasAnalysis ? '' : ' import-upload-panel'}${dragging ? ' is-dragging' : ''}`}
    aria-label={text('上传聊天文件', 'Upload conversation')}
    onDragOver={event => { event.preventDefault(); if (!busy) setDragging(true) }} onDragLeave={() => setDragging(false)}
    onDrop={event => { event.preventDefault(); setDragging(false); if (!confirming) void onSelectFiles(Array.from(event.dataTransfer.files)) }}>
    <input ref={inputRef} type="file" accept=".md" hidden aria-label={text('选择 Markdown 文件', 'Choose Markdown file')} disabled={confirming}
      onChange={event => { const files = Array.from(event.target.files ?? []); event.target.value = ''; if (files.length) void onSelectFiles(files) }} />
    {hasAnalysis ? <>
      <div className="import-file-info"><span className="import-file-icon" aria-hidden="true">MD</span>
        <strong>{file?.name}</strong><span>{fileInfo}</span><span className="import-file-status">{text('分析完成', 'Analyzed')}</span></div>
      <div className="import-file-actions">
        <button type="button" className="import-text-button" onClick={onShowPrompt}>{text('导出提示词', 'Export prompt')}</button>
        <button type="button" disabled={confirming} onClick={chooseFile}>{text('更换文件', 'Change file')}</button>
        <button type="button" disabled={busy} onClick={() => void onAnalyze()}>{analyzeLabel}</button>
      </div>
    </> : <>
      <div className="import-upload-heading"><span className="page-eyebrow">{text('聊天导入', 'Chat import')}</span>
        <h3>{text('导入一段日语练习聊天', 'Import a Japanese practice conversation')}</h3>
        <p>{text('让 AI 整理聊天中需要复习的表达，再由你逐项检查、编辑和确认。', 'Let AI find expressions to review, then check, edit, and confirm each item.')}</p></div>
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
