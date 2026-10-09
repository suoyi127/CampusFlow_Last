// @vitest-environment happy-dom
import { createApp, defineComponent, h, nextTick } from 'vue'
import { expect, test, vi } from 'vitest'
import SpacesPage from './SpacesPage.vue'
import { filters } from '../filters'

const mocks = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../api', () => ({ api: mocks.api }))
vi.mock('../components/LocationPicker.vue', () => ({ default: defineComponent({ emits: ['confirm'], setup: (_, { emit }) => () => h('button', { onClick: () => emit('confirm', { latitude: 30, longitude: 120, coordinateSystem: 'GCJ02', address: '新的起点' }) }, '确认测试起点') }) }))
vi.mock('../components/SpaceMap.vue', () => ({ default: defineComponent({ setup: () => () => null }) }))

test('a delayed old-origin response is discarded and immediately queries the confirmed origin', async () => {
  vi.useFakeTimers(); filters.latitude = 31.2304; filters.longitude = 121.4737
  let resolveOld!: (value: unknown) => void
  mocks.api.mockReset().mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    .mockResolvedValue({ items: [], total: 0, warnings: [] })
  const pass = defineComponent({ inheritAttrs: false, setup: (_, { slots }) => () => h('div', slots.default?.()) })
  const button = defineComponent({ setup: (_, { attrs, slots }) => () => h('button', { onClick: attrs.onClick }, slots.default?.()) })
  const empty = defineComponent({ setup: () => () => null })
  const app = createApp(SpacesPage)
  for (const name of ['ElDialog', 'ElCheckboxGroup', 'ElCheckbox', 'ElRadioGroup', 'ElRadioButton']) app.component(name, pass)
  for (const name of ['ElInput', 'ElInputNumber', 'ElSelect', 'ElOption', 'ElTag', 'ElAlert', 'ElEmpty', 'ElSkeleton']) app.component(name, empty)
  app.component('ElButton', button)
  app.component('RouterLink', pass)
  const root = document.createElement('div'); app.mount(root)
  try {
    Array.from(root.querySelectorAll('button')).find(node => node.textContent === '定位 / 地图选点')!.click()
    await nextTick(); Array.from(root.querySelectorAll('button')).find(node => node.textContent === '确认测试起点')!.click()
    await nextTick(); expect(mocks.api).toHaveBeenCalledTimes(1)
    resolveOld({ items: [], total: 123, warnings: [] })
    await Promise.resolve(); await Promise.resolve(); await nextTick()
    expect(root.textContent).not.toContain('123')
    await vi.advanceTimersByTimeAsync(0)
    expect(mocks.api).toHaveBeenCalledTimes(2)
    expect(mocks.api.mock.calls[1]![0]).toContain('latitude=30&longitude=120')
  } finally { app.unmount(); vi.useRealTimers(); filters.latitude = 31.2304; filters.longitude = 121.4737 }
})
