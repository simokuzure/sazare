export default function ManagementPlaceholder({
  title,
  description,
  items,
}: {
  title: string
  description: string
  items: string[]
}) {
  return (
    <section className="page-content" aria-label={title}>
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
