import { useLayoutEffect, useRef, useState } from 'react'
import { transcribeSpeech } from '../api/speechApi'
import { useLanguage } from '../i18n/LanguageContext'

const MAX_AUDIO_BYTES = 8 * 1024 * 1024
const MAX_SECONDS = 60
const AUDIO_TYPES = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus']
type Phase = 'idle' | 'requesting' | 'recording' | 'transcribing'
type Recording = {
  recorder: MediaRecorder | null
  stream: MediaStream | null
  chunks: Blob[]
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
  recording.stream?.getTracks().forEach((track) => track.stop())
  recording.stream = null
  recording.recorder = null
  recording.chunks = []
  recording.request.abort()
}

export function useSpeechInput(options: Options) {
  const { text, english } = useLanguage()
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

  function stop() {
    const recording = recordingRef.current
    if (!recording?.recorder || recording.recorder.state !== 'recording') return
    setPhase('transcribing')
    if (recording.timer !== null) clearInterval(recording.timer)
    recording.timer = null
    recording.recorder.stop()
    recording.stream?.getTracks().forEach((track) => track.stop())
    recording.stream = null
  }

  async function start() {
    if (!optionsRef.current.enabled || recordingRef.current || draft !== null) return
    setMessage('')
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setMessage(text('当前环境不支持录音，请使用 HTTPS 或 localhost 下的 Chrome/Edge，或直接输入文字。', 'Recording requires a supported browser on HTTPS or localhost. You can still type your answer.'))
      return
    }
    const mimeType = AUDIO_TYPES.find((type) => MediaRecorder.isTypeSupported(type))
    if (!mimeType) {
      setMessage(text('浏览器不支持所需录音格式，请使用 Chrome/Edge 或输入文字。', 'This browser does not support the required recording format. Use Chrome/Edge or type your answer.'))
      return
    }
    const recording: Recording = {
      recorder: null, stream: null, chunks: [], size: 0, timer: null, request: new AbortController(),
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
      recorder.onstop = async () => {
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
        try {
          const transcript = await transcribeSpeech(audio, recording.request.signal)
          if (recordingRef.current !== recording) return
          dispose()
          setPhase('idle')
          append(transcript)
        } catch (error) {
          fail(recording, english
            ? 'Transcription failed or no speech was recognized. Check your connection and record again.'
            : error instanceof Error ? error.message : '转写失败，请重新录音。')
        }
      }
      recorder.start(250)
      setPhase('recording')
      const startedAt = Date.now()
      recording.timer = setInterval(() => {
        if (recordingRef.current !== recording) return
        const elapsed = Math.floor((Date.now() - startedAt) / 1000)
        setSeconds(Math.min(elapsed, MAX_SECONDS))
        if (elapsed >= MAX_SECONDS) stop()
      }, 250)
    } catch (error) {
      const denied = error instanceof DOMException && error.name === 'NotAllowedError'
      fail(recording, denied
        ? text('未获得麦克风权限，请在浏览器中允许录音，或使用文字输入。', 'Microphone permission was denied. Allow it in your browser or type your answer.')
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
