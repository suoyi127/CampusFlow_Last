// @vitest-environment happy-dom
import { createApp, defineComponent, h, nextTick } from 'vue'
import { expect, test, vi } from 'vitest'
import SystemConfigPage from './SystemConfigPage.vue'
import type { RuntimeConfig } from '../systemTypes'
const mocks = vi.hoisted(() => ({ api: vi.fn(), poll: null as null | ((signal: AbortSignal) => Promise<void>) }))
vi.mock('../api', () => ({ api: mocks.api, jsonRequest: (method: string, body: unknown) => ({ method, body: JSON.stringify(body) }), ApiFailure: class extends Error { constructor(public status: number) { super('版本冲突') } } }))
vi.mock('../usePolling', () => ({ usePolling: (callback: (signal: AbortSignal) => Promise<void>) => { mocks.poll = callback; return { error: '', refresh: vi.fn() } } }))
const config = (version: number): RuntimeConfig => ({ version, simulationSeconds: 5, validitySeconds: 30, noiseWindowSeconds: 60, distanceWeight: .3, quietWeight: .3, freeWeight: .25, facilityWeight: .15 })
test('background configuration updates preserve input and require explicit reload', async () => {
  const controls: { props: { modelValue: unknown }; change: (value: unknown) => void }[] = []
  const control = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], setup(props, { emit }) { controls.push({ props, change: value => emit('update:modelValue', value) }); return () => null } })
  const pass = defineComponent({ inheritAttrs: false, setup: (_, { slots, attrs }) => () => h('div', [String(attrs.title ?? ''), slots.default?.()]) })
  const app = createApp(SystemConfigPage)
  app.component('ElInputNumber', control)
  for (const name of ['ElAlert', 'ElButton']) app.component(name, pass)
  const root = document.createElement('div'); app.mount(root)
  try {
    mocks.api.mockResolvedValueOnce(config(1)); await mocks.poll!(new AbortController().signal); await nextTick()
    controls[0]!.change(10); await nextTick()
    mocks.api.mockResolvedValueOnce({ ...config(2), simulationSeconds: 8 }); await mocks.poll!(new AbortController().signal); await nextTick()
    expect(controls[0]!.props.modelValue).toBe(10)
    expect(root.textContent).toContain('服务器配置已更新')
    // 后来的旧 GET 也不能降低提示版本并恢复保存按钮。
    mocks.api.mockResolvedValueOnce(config(1)); await mocks.poll!(new AbortController().signal); await nextTick()
    expect(root.textContent).toContain('当前服务器版本 2')
    expect(controls[0]!.props.modelValue).toBe(10)
  } finally { app.unmount() }
})
