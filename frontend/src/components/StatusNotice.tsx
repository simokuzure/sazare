import type { PracticeNotice } from '../types/api'

type StatusNoticeProps = {
  notice: PracticeNotice
  className?: string
  actionLabel?: string
  onAction?: () => void
}

export default function StatusNotice({ notice, className, actionLabel, onAction }: StatusNoticeProps) {
  const classes = ['notice', notice.kind === 'error' ? 'is-error' : null, className].filter(Boolean).join(' ')
  return (
    <div className={classes} role={notice.kind === 'error' ? 'alert' : 'status'}>
      <strong>{notice.title}</strong>
      <p>{notice.message}</p>
      {actionLabel && onAction ? <button type="button" onClick={onAction}>{actionLabel}</button> : null}
    </div>
  )
}
