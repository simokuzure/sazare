import { useEffect, useRef, useState } from 'react'
import { useLanguage } from '../../i18n/LanguageContext'

export default function ChatImportSourceDialog({ prompt, onClose }: {
  prompt: string; onClose: () => void
}) {
  const { text } = useLanguage()
  const dialog = useRef<HTMLDialogElement>(null)
  const [copyStatus, setCopyStatus] = useState<'idle' | 'copied' | 'failed'>('idle')

  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  async function copyPrompt() {
    try {
      await navigator.clipboard.writeText(prompt)
      setCopyStatus('copied')
    } catch {
      setCopyStatus('failed')
    }
  }

  // 开发模式重新打开弹窗后，忽略此前关闭操作排队触发的事件。
  return <dialog ref={dialog} className="import-source-dialog" aria-labelledby="import-source-title"
    onClose={event => { if (!event.currentTarget.open) onClose() }}>
    <header className="import-dialog-heading"><h3 id="import-source-title">{text('导出提示词', 'Export prompt')}</h3>
      <button type="button" onClick={() => dialog.current?.close()}>{text('关闭', 'Close')}</button></header>
    <pre>{prompt}</pre>
    <div className="import-dialog-actions">
      {copyStatus === 'copied' ? <span role="status">{text('提示词已复制，可粘贴到需要导出的聊天中。', 'Prompt copied. Paste it into the conversation to export.')}</span> : null}
      {copyStatus === 'failed' ? <span role="alert">{text('无法自动复制，请手动选择并复制上方提示词。', 'Could not copy automatically. Select and copy the prompt above.')}</span> : null}
      <button type="button" onClick={() => void copyPrompt()}>{text('复制导出提示词', 'Copy export prompt')}</button>
    </div>
  </dialog>
}
