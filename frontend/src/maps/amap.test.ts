// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../api', () => ({ api: mocks.api }))
let loadAMap: typeof import('./amap')['loadAMap']
beforeEach(async () => {
  vi.resetModules(); mocks.api.mockReset(); loadAMap = (await import('./amap')).loadAMap
  const create = document.createElement.bind(document)
  // SDK 加载事件由测试驱动，不允许 DOM 模拟器联网或自动触发加载失败。
  vi.spyOn(document, 'createElement').mockImplementation((tag, options) => {
    const element = create(tag, options)
    if (tag === 'script') element.setAttribute('type', 'application/x-campusflow-test')
    return element
  })
})

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllEnvs(); vi.useRealTimers(); delete window.AMap; document.querySelectorAll('script[data-campusflow-amap]').forEach(node => node.remove()) })

test('missing public key does not load third party scripts', async () => {
  mocks.api.mockResolvedValue({ configured: false, key: '' })
  await expect(loadAMap()).rejects.toThrow('未配置')
  expect(document.querySelector('script[data-campusflow-amap]')).toBeNull()
})

test('concurrent map components share SDK loading and only send serviceHost to the browser', async () => {
  mocks.api.mockResolvedValue({ configured: true, key: 'public-test-key' })
  const first = loadAMap(), second = loadAMap()
  await Promise.resolve()
  expect(document.querySelectorAll('script[data-campusflow-amap]')).toHaveLength(1)
  window.AMap = { plugin: (_names: string[], done: () => void) => done() } as unknown as NonNullable<Window['AMap']>
  document.querySelector<HTMLScriptElement>('script[data-campusflow-amap]')!.dispatchEvent(new Event('load'))
  expect(await first).toBe(await second)
  expect(window._AMapSecurityConfig).toEqual({ serviceHost: `${window.location.origin}/_AMapService` })
  expect(JSON.stringify(window._AMapSecurityConfig)).not.toContain('securityJsCode')
})

test('failed SDK loads time out and allow an explicit retry', async () => {
  vi.useFakeTimers(); mocks.api.mockResolvedValue({ configured: true, key: 'public-test-key' })
  const failed = expect(loadAMap()).rejects.toThrow('加载失败')
  await vi.advanceTimersByTimeAsync(12001); await failed
  expect(document.querySelector('script[data-campusflow-amap]')).toBeNull()
  const retry = loadAMap(); await Promise.resolve()
  window.AMap = { plugin: (_names: string[], done: () => void) => done() } as unknown as NonNullable<Window['AMap']>
  document.querySelector<HTMLScriptElement>('script[data-campusflow-amap]')!.dispatchEvent(new Event('load'))
  await expect(retry).resolves.toBe(window.AMap)
})
