/* global AudioWorkletProcessor, registerProcessor */
class PcmRecorder extends AudioWorkletProcessor {
  constructor() {
    super()
    this.samples = new Int16Array(1600)
    this.offset = 0
    this.active = true
    this.port.onmessage = () => {
      this.active = false
      this.flush()
      this.port.postMessage('flushed')
    }
  }

  flush() {
    if (!this.offset) return
    const buffer = new ArrayBuffer(this.offset * 2)
    const view = new DataView(buffer)
    for (let i = 0; i < this.offset; i++) view.setInt16(i * 2, this.samples[i], true)
    this.port.postMessage(buffer, [buffer])
    this.offset = 0
  }

  process(inputs) {
    if (!this.active) return false
    const channels = inputs[0]
    if (!channels?.length) return true
    for (let i = 0; i < channels[0].length; i++) {
      let value = 0
      for (const channel of channels) value += channel[i] / channels.length
      const sample = Math.max(-1, Math.min(1, value))
      this.samples[this.offset++] = Math.round(sample * (sample < 0 ? 32768 : 32767))
      if (this.offset === this.samples.length) this.flush()
    }
    return true
  }
}

registerProcessor('pcm-recorder', PcmRecorder)
