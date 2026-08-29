export type ScoreTone = 'perfect' | 'excellent' | 'good' | 'warning' | 'danger' | 'muted'

export function getScoreTone(score: number | null | undefined): ScoreTone {
  if (score == null || !Number.isFinite(score)) return 'muted'
  if (score === 100) return 'perfect'
  if (score >= 90) return 'excellent'
  if (score >= 75) return 'good'
  if (score >= 60) return 'warning'
  return 'danger'
}

export function scoreToneClassName(score: number | null | undefined, className?: string) {
  return [className, 'score-value', `is-${getScoreTone(score)}`].filter(Boolean).join(' ')
}
