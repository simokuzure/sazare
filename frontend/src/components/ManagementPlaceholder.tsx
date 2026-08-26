import { useLanguage } from '../i18n/LanguageContext'

export default function ManagementPlaceholder({
  title,
  description,
  items,
}: {
  title: string
  description: string
  items: string[]
}) {
  const { text } = useLanguage()

  return (
    <section className="page-content" aria-label={title}>
      <section className="surface placeholder-panel">
        <div>
          <span className="label">{text('当前状态', 'Status')}</span>
          <strong>{text('入口已预留，功能待接入', 'This section is not available yet')}</strong>
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
