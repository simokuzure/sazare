export function splitArticleParagraphs(text: string) {
  const normalized = text.replace(/\r\n?/g, '\n').trim()
  if (!normalized) return []
  return normalized.split(/\n\s*\n/).map((segment) => segment.trim()).filter(Boolean)
}
