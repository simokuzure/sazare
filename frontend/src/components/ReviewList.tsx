import type { ReactNode } from 'react'

export default function ReviewList<T>({
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
