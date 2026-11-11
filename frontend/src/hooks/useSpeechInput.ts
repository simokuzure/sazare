import { useLayoutEffect, useRef, useState } from 'react'
import { getSpeechInputConfig, openSpeechSession, transcribeSpeech, type SpeechSession } from '../api/speechApi'
import pcmWorkletUrl from '../audio/pcm-recorder.worklet.js?url'
import { useLanguage } from '../i18n/LanguageContext'
import { getErrorMessage } from '../api/client'

const MAX_AUDIO_BYTES = 8 * 1024 * 1024
const MAX_SECONDS = 60
const AUDIO_TYPES = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus']
type Phase = 'idle' | 'requesting' | 'recording' | 'transcribing'
type Recording = {
  recorder: MediaRecorder | null
  chunks: Blob[]
  stopping: boolean
  context: AudioContext | null
  source: MediaStreamAudioSourceNode | null
  processor: AudioWorkletNode | null
  session: SpeechSession | null
  flushed: (() => void) | null
  stream: MediaStream | null
  size: number
  timer: ReturnType<typeof setInterval> | null
  request: AbortController
}

type Options = {
  contextKey: string
  enabled: boolean
  value: string
  maxLength: number
  onChange: (value: string) => void
}

function releaseRecording(recording: Recording) {
  if (recording.timer !== null) clearInterval(recording.timer)
  recording.timer = null
  if (recording.recorder) {
    recording.recorder.ondataavailable = null
    recording.recorder.onstop = null
    recording.recorder.onerror = null
    if (recording.recorder.state !== 'inactive') recording.recorder.stop()
  }
  recording.chunks = []
  recording.source?.disconnect()
  if (recording.processor) {
    recording.processor.port.onmessage = null
    recording.processor.onprocessorerror = null
    recording.processor.disconnect()
  }
  if (recording.context) void recording.context.close().catch(() => {})
  recording.session?.close()
  recording.flushed?.()
  recording.stream?.getTracks().forEach((track) => track.stop())
  recording.stream = null
  recording.request.abort()
}

export function useSpeechInput(options: Options) {
  const { text } = useLanguage()
  const [phase, setPhase] = useState<Phase>('idle')
  const [seconds, setSeconds] = useState(0)
  const [message, setMessage] = useState('')
  const [draft, setDraft] = useState<string | null>(null)
  const recordingRef = useRef<Recording | null>(null)
  const optionsRef = useRef(options)

  useLayoutEffect(() => { optionsRef.current = options })

  function dispose() {
    const recording = recordingRef.current
    recordingRef.current = null
    if (recording) releaseRecording(recording)
  }

  function cancel() {
    dispose()
    setPhase('idle')
    setSeconds(0)
    setMessage('')
    setDraft(null)
  }

  // 练习页会保留隐藏模块，不能只依赖卸载来关闭麦克风。
  useLayoutEffect(() => {
    setPhase('idle')
    setSeconds(0)
    setMessage('')
    setDraft(null)
    const onPageHide = () => cancel()
    window.addEventListener('pagehide', onPageHide)
    return () => {
      dispose()
      window.removeEventListener('pagehide', onPageHide)
    }
  }, [options.contextKey, options.enabled])

  function append(transcript: string) {
    const current = optionsRef.current
    if (!current.enabled) return
    const value = current.value ? `${current.value}\n${transcript}` : transcript
    if (value.length > current.maxLength) {
      setDraft(transcript)
      setMessage(text('追加后超过字数限制，请缩短下方转写文字后再追加。', 'The combined text is too long. Shorten the transcript below, then append it.'))
      return
    }
    current.onChange(value)
    setDraft(null)
    setMessage('')
  }

  function fail(recording: Recording, message: string) {
    if (recordingRef.current !== recording) return
    dispose()
    setPhase('idle')
    setMessage(message)
  }

  async function complete(recording: Recording, result: Promise<string>) {
    try {
      const transcript = await result
      if (recordingRef.current !== recording) return
      dispose()
      setPhase('idle')
      append(transcript)
    } catch (error) {
      fail(recording, error instanceof Error ? getErrorMessage(error) : text('转写失败，请重新录音。', 'Transcription failed. Please record again.'))
    }
  }

  function startFileRecording(recording: Recording, stream: MediaStream) {
    const mimeType = typeof MediaRecorder === 'undefined' ? undefined
      : AUDIO_TYPES.find((type) => MediaRecorder.isTypeSupported(type))
    if (!mimeType) {
      throw new Error(text('浏览器不支持所需录音格式，请使用 Chrome/Edge 或输入文字。', 'This browser does not support the required recording format. Use Chrome/Edge or type your answer.'))
    }
    const recorder = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 64000 })
    recording.recorder = recorder
    recorder.ondataavailable = (event) => {
      if (recordingRef.current !== recording) return
      recording.size += event.data.size
      if (recording.size > MAX_AUDIO_BYTES) {
        fail(recording, text('音频超过 8 MiB，请缩短内容后重新录音。', 'Audio exceeded 8 MiB. Record a shorter answer.'))
        return
      }
      if (event.data.size) recording.chunks.push(event.data)
    }
    recorder.onerror = () => fail(recording, text('录音失败，请检查麦克风后重试。', 'Recording failed. Check your microphone and try again.'))
    recorder.onstop = () => {
      if (recordingRef.current !== recording) return
      if (recording.timer !== null) clearInterval(recording.timer)
      recording.timer = null
      recording.stream?.getTracks().forEach((track) => track.stop())
      recording.stream = null
      setPhase('transcribing')
      const audio = new Blob(recording.chunks, { type: recorder.mimeType })
      recording.chunks = []
      if (!audio.size) {
        fail(recording, text('录音为空，请重新录音。', 'The recording is empty. Please record again.'))
        return
      }
      void complete(recording, transcribeSpeech(audio, recording.request.signal))
    }
    recorder.start(250)
  }

  async function stop() {
    const recording = recordingRef.current
    if (!recording || recording.stopping) return
    if (recording.recorder ? recording.recorder.state !== 'recording' : !recording.processor || !recording.session) return
    recording.stopping = true
    setPhase('transcribing')
    if (recording.timer !== null) clearInterval(recording.timer)
    recording.timer = null
    if (recording.recorder) {
      recording.recorder.stop()
      recording.stream?.getTracks().forEach((track) => track.stop())
      recording.stream = null
      return
    }
    recording.source?.disconnect()
    recording.stream?.getTracks().forEach((track) => track.stop())
    recording.stream = null
    try {
      await new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => reject(new Error('实时语音转写超时，请重新录音')), 3000)
        recording.flushed = () => { clearTimeout(timeout); resolve() }
        recording.processor!.port.postMessage('flush')
      })
      if (recordingRef.current !== recording) return
      if (!recording.size) {
        fail(recording, text('录音为空，请重新录音。', 'The recording is empty. Please record again.'))
        return
      }
      await complete(recording, recording.session!.finish())
    } catch (error) {
      fail(recording, error instanceof Error ? getErrorMessage(error) : text('转写失败，请重新录音。', 'Transcription failed. Please record again.'))
    }
  }

  async function start() {
    if (!optionsRef.current.enabled || recordingRef.current || draft !== null) return
    setMessage('')
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setMessage(text('当前环境不支持录音，请使用 HTTPS 或 localhost 下的 Chrome/Edge，或直接输入文字。', 'Recording requires a supported browser on HTTPS or localhost. You can still type your answer.'))
      return
    }
    const recording: Recording = {
      recorder: null, chunks: [], stopping: false,
      context: null, source: null, processor: null, session: null, flushed: null,
      stream: null, size: 0, timer: null, request: new AbortController(),
    }
    recordingRef.current = recording
    setSeconds(0)
    setPhase('requesting')
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      if (recordingRef.current !== recording) {
        stream.getTracks().forEach((track) => track.stop())
        return
      }
      recording.stream = stream
      const config = await getSpeechInputConfig(recording.request.signal)
      if (recordingRef.current !== recording) return
      if (config.mode === 'file') {
        startFileRecording(recording, stream)
      } else {
        if (typeof AudioContext === 'undefined' || typeof AudioWorkletNode === 'undefined') {
          throw new Error(text('当前环境不支持实时录音，请使用 Chrome/Edge 或切换为 file 模式。', 'Live recording requires Chrome/Edge. You can also switch to file mode.'))
        }
        const context = new AudioContext({ sampleRate: 16000 })
        recording.context = context
        await context.resume()
        if (recordingRef.current !== recording) return
        await context.audioWorklet.addModule(pcmWorkletUrl)
        if (recordingRef.current !== recording) return
        const session = await openSpeechSession(recording.request.signal, (error) => fail(recording, getErrorMessage(error)))
        if (recordingRef.current !== recording) { session.close(); return }
        recording.session = session
        const processor = new AudioWorkletNode(context, 'pcm-recorder')
        recording.processor = processor
        processor.port.onmessage = (event: MessageEvent<ArrayBuffer | string>) => {
          if (recordingRef.current !== recording) return
          if (event.data === 'flushed') { recording.flushed?.(); return }
          if (!(event.data instanceof ArrayBuffer)) return
          recording.size += event.data.byteLength
          if (recording.size > MAX_AUDIO_BYTES) {
            fail(recording, text('音频超过 8 MiB，请缩短内容后重新录音。', 'Audio exceeded 8 MiB. Record a shorter answer.'))
            return
          }
          session.send(event.data)
        }
        processor.onprocessorerror = () => fail(recording, text('录音失败，请检查麦克风后重试。', 'Recording failed. Check your microphone and try again.'))
        const source = context.createMediaStreamSource(stream)
        recording.source = source
        source.connect(processor)
        processor.connect(context.destination)
      }
      setPhase('recording')
      const startedAt = Date.now()
      recording.timer = setInterval(() => {
        if (recordingRef.current !== recording) return
        const elapsed = Math.floor((Date.now() - startedAt) / 1000)
        setSeconds(Math.min(elapsed, MAX_SECONDS))
        if (elapsed >= MAX_SECONDS) void stop()
      }, 250)
    } catch (error) {
      const denied = error instanceof DOMException && error.name === 'NotAllowedError'
      fail(recording, denied
        ? text('未获得麦克风权限，请在浏览器中允许录音，或使用文字输入。', 'Microphone permission was denied. Allow it in your browser or type your answer.')
        : error instanceof Error ? getErrorMessage(error)
          : text('无法使用麦克风，请检查设备或使用文字输入。', 'The microphone is unavailable. Check the device or type your answer.'))
    }
  }

  return {
    phase, seconds, message, draft, setDraft,
    busy: phase !== 'idle',
    enabled: options.enabled,
    start, stop, cancel,
    appendDraft: () => { if (draft?.trim()) append(draft.trim()) },
  }
}

export type SpeechInputState = ReturnType<typeof useSpeechInput>
