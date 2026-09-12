import { readApiResponse } from './client'

export async function transcribeSpeech(audio: Blob, signal: AbortSignal): Promise<string> {
  const response = await fetch('/api/speech-transcriptions', {
    method: 'POST',
    headers: { 'Content-Type': audio.type },
    body: audio,
    signal,
    cache: 'no-store',
  })
  const result = await readApiResponse<{ text: string }>(response)
  if (typeof result.data?.text !== 'string' || !result.data.text.trim()) {
    throw new Error('未识别到语音，请重新录音')
  }
  return result.data.text.trim()
}
