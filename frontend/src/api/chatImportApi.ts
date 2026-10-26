import type { ChatImportAnalysis, ChatImportConfirmation, ChatImportPayload } from '../types/chatImport'

// 确认请求没有幂等标识，只有完整的业务响应才能判定为明确成功或拒绝。
export class ChatImportRequestError extends Error {
  readonly uncertain: boolean
  constructor(message: string, uncertain: boolean) {
    super(message)
    this.name = 'ChatImportRequestError'
    this.uncertain = uncertain
  }
}

async function request<T>(url: string, options: RequestInit, valid: (data: unknown) => data is T): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, { ...options, cache: 'no-store' })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ChatImportRequestError('网络连接中断，请检查连接。', true)
  }
  let result: { code?: unknown; message?: unknown; data?: unknown } | null
  try {
    result = await response.json()
  } catch {
    throw new ChatImportRequestError('服务器未返回完整结果。', true)
  }
  if (!result || typeof result.code !== 'number') throw new ChatImportRequestError('服务器响应格式不正确。', true)
  if (result.code !== 0) {
    throw new ChatImportRequestError(typeof result.message === 'string' ? result.message : '请求被拒绝。', false)
  }
  if (!response.ok || !valid(result.data)) throw new ChatImportRequestError('服务器未返回完整结果。', true)
  return result.data
}

export function analyzeChatImport(file: File, learningMode: string, signal: AbortSignal): Promise<ChatImportAnalysis> {
  return request(`/api/chat-imports/analyses?${new URLSearchParams({ learningMode })}`, {
    method: 'POST', headers: { 'Content-Type': 'text/markdown' }, body: file, signal,
  }, (data): data is ChatImportAnalysis => {
    if (!data || typeof data !== 'object') return false
    const value = data as ChatImportAnalysis
    return Array.isArray(value.checkedUserMessageIds) && value.checkedUserMessageIds.every(Number.isInteger)
      && Array.isArray(value.candidates) && value.candidates.length <= 200 && value.candidates.every(c =>
        c && Number.isInteger(c.errorTypeId) && ['name', 'description', 'correctedExpression', 'reviewSourceText', 'referenceAnswer']
          .every(key => typeof c[key as keyof typeof c] === 'string')
        && ['LOW', 'MEDIUM', 'HIGH'].includes(c.severity) && Array.isArray(c.evidence)
        && c.evidence.every(e => e && Number.isInteger(e.messageId) && typeof e.originalText === 'string'))
  })
}

export async function confirmChatImport(payload: ChatImportPayload): Promise<ChatImportConfirmation> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 30_000)
  try {
    return await request('/api/chat-imports/confirmations', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), signal: controller.signal,
    }, (data): data is ChatImportConfirmation => {
      if (!data || typeof data !== 'object') return false
      const value = data as ChatImportConfirmation
      return Array.isArray(value.cards) && value.cards.length > 0 && value.cards.every(card => card
        && Number.isInteger(card.cardId) && Number.isInteger(card.userErrorTypeId)
        && Array.isArray(card.questionIds) && card.questionIds.every(Number.isInteger)
        && ['CREATED', 'UPDATED', 'REACTIVATED'].includes(card.action))
    })
  } finally { clearTimeout(timer) }
}
