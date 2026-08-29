export default function StatusBadge({ label, value }: { label: string; value: string }) {
  const available = value === 'UP'

  return (
    <div className={available ? 'status-badge is-up' : 'status-badge'} role="status" aria-live="polite">
      <i aria-hidden="true" />
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}
