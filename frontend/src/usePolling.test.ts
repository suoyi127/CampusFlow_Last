import { createRenderer, defineComponent } from 'vue'
import { afterEach, expect, test, vi } from 'vitest'
import { usePolling } from './usePolling'
import { metricIsRecent, recommendationIsStale, type SpaceStatus } from './types'

function mountPolling(load: (signal: AbortSignal) => Promise<void>) {
  const renderer = createRenderer<Record<string, unknown>, Record<string, unknown>>({
    insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
    setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {},
  })
  let polling!: ReturnType<typeof usePolling>
  const app = renderer.createApp(defineComponent({ setup() { polling = usePolling(load); return () => null } }))
  app.mount({})
  return { polling, unmount: () => app.unmount() }
}
afterEach(() => vi.useRealTimers())

test('old sample ages independently of the successful request timestamp', () => {
  const status = { calculatedAt: '2026-10-09T02:00:29Z', noiseUpdatedAt: '2026-10-09T02:00:00Z',
    noiseState: 'VALID', peopleState: 'MISSING', validitySeconds: 30 } as SpaceStatus
  expect(metricIsRecent(status, status.noiseUpdatedAt, status.noiseState, 1)).toBe(true)
  expect(metricIsRecent(status, status.noiseUpdatedAt, status.noiseState, 2)).toBe(false)
  expect(recommendationIsStale(status, 2)).toBe(true)
})

test('a hung request times out and polling recovers without leaving the page', async () => {
  vi.useFakeTimers()
  let attempts = 0
  const mounted = mountPolling(signal => {
    attempts++
    if (attempts > 1) return Promise.resolve()
    return new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(signal.reason)))
  })
  try {
    await vi.advanceTimersByTimeAsync(9000)
    expect(mounted.polling.error.value).toContain('超时')
    await vi.advanceTimersByTimeAsync(2000)
    expect(attempts).toBeGreaterThan(1)
    expect(mounted.polling.error.value).toBe('')
  } finally { mounted.unmount() }
})

test('leaving the page aborts the current request and cancels future polls', async () => {
  vi.useFakeTimers()
  let attempts = 0
  let pending: AbortSignal | undefined
  const mounted = mountPolling(signal => { attempts++; pending=signal; return new Promise(() => {}) })
  mounted.unmount()
  await vi.advanceTimersByTimeAsync(15000)
  expect(pending?.aborted).toBe(true)
  expect(attempts).toBe(1)
})
