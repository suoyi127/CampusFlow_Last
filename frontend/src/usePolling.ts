import { onMounted, onUnmounted, ref } from 'vue'

export function usePolling(load: (signal: AbortSignal) => Promise<void>) {
  const loading = ref(false)
  const error = ref('')
  const receivedAt = ref(0)
  const elapsedSeconds = ref(0)
  let active = true
  let pending: AbortController | null = null
  let timer: ReturnType<typeof setInterval> | undefined
  let clockTimer: ReturnType<typeof setInterval> | undefined
  async function refresh() {
    if (pending || !active) return
    pending = new AbortController(); loading.value = true
    const timeout = setTimeout(() => pending?.abort(new Error('请求超时，请重试')), 8000)
    try {
      await load(pending.signal)
      if (!active) return
      error.value = ''; receivedAt.value = performance.now(); elapsedSeconds.value = 0
    } catch (failure) {
      if (active && !(failure instanceof DOMException && failure.name === 'AbortError'))
        error.value = failure instanceof Error ? failure.message : '连接失败，请重试'
    } finally { clearTimeout(timeout); pending = null; if (active) loading.value = false }
  }
  onMounted(() => {
    void refresh()
    timer = setInterval(() => void refresh(), 5000)
    clockTimer = setInterval(() => { if (receivedAt.value) elapsedSeconds.value = (performance.now() - receivedAt.value) / 1000 }, 1000)
  })
  onUnmounted(() => { active = false; pending?.abort(); clearInterval(timer); clearInterval(clockTimer) })
  return { loading, error, elapsedSeconds, refresh }
}
