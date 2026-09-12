import { useId, type ReactNode } from 'react'
import { useLanguage } from '../i18n/LanguageContext'
import type { SpeechInputState } from '../hooks/useSpeechInput'

export default function SpeechInput({ speech, children }: { speech: SpeechInputState; children: ReactNode }) {
  const { text } = useLanguage()
  const draftId = useId()
  return <div className="speech-input">
    <div className="speech-input-toolbar">
      <div className="speech-input-controls">
        {speech.phase === 'idle' ? <button type="button" disabled={!speech.enabled || speech.draft !== null} onClick={speech.start}>{text('语音输入', 'Voice input')}</button> : null}
        {speech.phase === 'recording' ? <button type="button" onClick={speech.stop}>{text('停止并转写', 'Stop and transcribe')}</button> : null}
        {speech.phase === 'recording' ? <span className="speech-input-timer" role="status" aria-live="polite">{text(`${speech.seconds} / 60 秒`, `${speech.seconds} / 60 s`)}</span> : null}
        {speech.phase === 'requesting' || speech.phase === 'transcribing' ? <button type="button" disabled aria-live="polite">{speech.phase === 'requesting' ? text('等待授权…', 'Awaiting permission…') : text('正在转写…', 'Transcribing…')}</button> : null}
        {speech.busy || speech.draft !== null ? <button type="button" onClick={speech.cancel}>{text('取消', 'Cancel')}</button> : null}
      </div>
      {children}
    </div>
    <p className="speech-input-status" role="status" aria-live="polite">{speech.message}</p>
    {speech.draft !== null ? <div className="speech-input-draft">
      <label htmlFor={draftId}>{text('待追加的转写文字', 'Transcript to append')}</label>
      <textarea id={draftId} value={speech.draft} onChange={(event) => speech.setDraft(event.target.value)} />
      <button type="button" disabled={!speech.draft.trim()} onClick={speech.appendDraft}>{text('追加文字', 'Append text')}</button>
    </div> : null}
  </div>
}
