import { readApiResponse } from './client'

type SessionConfig = { token: string; websocketUrl: string; setup: Record<string, unknown>; mock: boolean }
export type SpeechSession = { send: (pcm: ArrayBuffer) => void; finish: () => Promise<string>; close: () => void }

export async function getSpeechInputConfig(signal: AbortSignal): Promise<{ mode: 'live' | 'file'; model: string }> {
  const response = await fetch('/api/speech-transcription-config', { signal, cache: 'no-store' })
  const config = (await readApiResponse<{ mode: string; model: string }>(response)).data
  if (!config || (config.mode !== 'live' && config.mode !== 'file') || typeof config.model !== 'string') {
    throw new Error('语音输入配置格式错误，请检查后端配置')
  }
  return { mode: config.mode, model: config.model }
}

export async function transcribeSpeech(audio: Blob, signal: AbortSignal): Promise<string> {
  const response = await fetch('/api/speech-transcriptions', {
    method: 'POST', headers: { 'Content-Type': audio.type }, body: audio, signal, cache: 'no-store',
  })
  const result = await readApiResponse<{ text: string }>(response)
  if (typeof result.data?.text !== 'string' || !result.data.text.trim()) {
    throw new Error('未识别到语音，请重新录音')
  }
  return result.data.text.trim()
}

const SESSION_FAILED = '实时语音转写连接失败，请检查网络或稍后重试'
const SESSION_TIMEOUT = '实时语音转写超时，请重新录音'
const INVALID_RESPONSE = '语音转写结果格式错误，请重新录音'
const MAX_TRANSCRIPT_LENGTH = 30_000

export async function openSpeechSession(signal: AbortSignal, onError: (error: Error) => void): Promise<SpeechSession> {
  const response = await fetch('/api/speech-transcription-sessions', {
    method: 'POST', signal, cache: 'no-store',
  })
  const config = (await readApiResponse<SessionConfig>(response)).data
  signal.throwIfAborted()
  if (config?.mock === true) {
    return { send() {}, close() {}, finish: async () => 'これは模擬の音声入力です。' }
  }
  if (!config || typeof config.token !== 'string' || !config.token
    || typeof config.websocketUrl !== 'string' || !config.websocketUrl.startsWith('wss://') || !config.setup) {
    throw new Error(INVALID_RESPONSE)
  }
  const url = new URL(config.websocketUrl)
  url.searchParams.set('access_token', config.token)
  const socket = new WebSocket(url)
  socket.binaryType = 'arraybuffer'
  let closed = false
  let stopping = false
  let generationComplete = false
  let transcriptFinished = false
  let transcript = ''
  let terminalError: Error | null = null
  let resolveReady!: () => void
  let rejectReady!: (error: Error) => void
  const ready = new Promise<void>((resolve, reject) => { resolveReady = resolve; rejectReady = reject })
  let resolveResult: ((text: string) => void) | undefined
  let rejectResult: ((error: Error) => void) | undefined
  let timer = setTimeout(() => fail(new Error(SESSION_TIMEOUT)), 15_000)

  function close() {
    if (closed) return
    closed = true
    clearTimeout(timer)
    signal.removeEventListener('abort', abort)
    const error = terminalError ?? new DOMException('Aborted', 'AbortError')
    rejectReady(error)
    rejectResult?.(error)
    socket.onopen = socket.onmessage = socket.onerror = socket.onclose = null
    socket.close()
  }

  function fail(error: Error) {
    if (closed) return
    terminalError = error
    rejectReady(error)
    rejectResult?.(error)
    close()
    onError(error)
  }

  function abort() { fail(new DOMException('Aborted', 'AbortError')) }
  signal.addEventListener('abort', abort, { once: true })
  if (signal.aborted) abort()

  function send(message: unknown) {
    if (closed || socket.readyState !== WebSocket.OPEN || socket.bufferedAmount > 256 * 1024) {
      throw new Error(SESSION_FAILED)
    }
    socket.send(JSON.stringify(message))
  }

  function complete() {
    // 专用 Live 转写返回 generationComplete；最终输入转写可独立到达。
    if (!stopping || !generationComplete || !transcriptFinished) return
    if (!transcript.trim()) { fail(new Error('未识别到语音，请重新录音')); return }
    resolveResult?.(transcript.trim())
    close()
  }

  socket.onopen = () => {
    try { send({ setup: config.setup }) } catch { fail(new Error(SESSION_FAILED)) }
  }
  socket.onmessage = (event) => {
    if (closed) return
    try {
      const raw = typeof event.data === 'string' ? event.data : new TextDecoder().decode(event.data)
      if (raw.length > 256 * 1024) throw new Error(INVALID_RESPONSE)
      const message = JSON.parse(raw)
      if (message.error || message.goAway) { fail(new Error(SESSION_FAILED)); return }
      if (message.setupComplete) {
        clearTimeout(timer)
        send({ realtimeInput: { activityStart: {} } })
        resolveReady()
      }
      const content = message.serverContent
      if (!content) return
      if (content.inputTranscription) {
        const part = content.inputTranscription
        if (part.text !== undefined && typeof part.text !== 'string') throw new Error(INVALID_RESPONSE)
        transcript += part.text ?? ''
        if (transcript.length > MAX_TRANSCRIPT_LENGTH) throw new Error(INVALID_RESPONSE)
        if (part.finished !== false) transcriptFinished = true
      }
      if (content.generationComplete === true || content.turnComplete === true) generationComplete = true
      complete()
    } catch { fail(new Error(INVALID_RESPONSE)) }
  }
  socket.onerror = () => fail(new Error(SESSION_FAILED))
  socket.onclose = () => fail(new Error(SESSION_FAILED))
  await ready
  return {
    send(pcm) {
      if (stopping) return
      try {
        const bytes = new Uint8Array(pcm)
        send({ realtimeInput: { audio: { mimeType: 'audio/pcm;rate=16000',
          data: btoa(String.fromCharCode(...bytes)) } } })
      } catch { fail(new Error(SESSION_FAILED)) }
    },
    finish() {
      if (terminalError) return Promise.reject(terminalError)
      if (closed || stopping) return Promise.reject(new Error(SESSION_FAILED))
      stopping = true
      return new Promise<string>((resolve, reject) => {
        resolveResult = resolve
        rejectResult = reject
        timer = setTimeout(() => fail(new Error(SESSION_TIMEOUT)), 20_000)
        try { send({ realtimeInput: { activityEnd: {} } }); complete() }
        catch { fail(new Error(SESSION_FAILED)) }
      })
    },
    close,
  }
}
