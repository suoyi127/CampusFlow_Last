// @vitest-environment happy-dom
import { createApp, defineComponent, h, nextTick } from 'vue'
import { expect, test, vi } from 'vitest'
import LocationPicker from './LocationPicker.vue'
import type { AMapSdk, MapEvent } from '../maps/types'
const mocks = vi.hoisted(() => ({ load: vi.fn() }))
vi.mock('../maps/amap', () => ({ loadAMap: mocks.load }))

function mount() {
  const confirm = vi.fn(), cancel = vi.fn()
  const app = createApp(LocationPicker, { initial: { longitude: 121, latitude: 31, coordinateSystem: 'GCJ02' }, onConfirm: confirm, onCancel: cancel })
  const pass = defineComponent({ inheritAttrs: false, setup: (_, { slots, attrs }) => () => h('div', [String(attrs.title ?? ''), slots.default?.()]) })
  const button = defineComponent({ inheritAttrs: false, setup: (_, { slots, attrs }) => () => h('button', { disabled: attrs.disabled, onClick: attrs.onClick as () => void }, slots.default?.()) })
  const empty = defineComponent({ setup: () => () => null })
  app.component('ElButton', button); app.component('ElAlert', pass); app.component('ElInput', empty); app.component('ElInputNumber', empty)
  const root = document.createElement('div'); app.mount(root)
  return { root, confirm, cancel, unmount: () => app.unmount() }
}

test('map failure and cancel do not apply or overwrite the parent position', async () => {
  mocks.load.mockRejectedValueOnce(new Error('地图未配置'))
  const mounted = mount()
  try {
    await Promise.resolve(); await nextTick()
    expect(mounted.root.textContent).toContain('地图未配置')
    Array.from(mounted.root.querySelectorAll('button')).find(button => button.textContent === '取消')!.click()
    expect(mounted.cancel).toHaveBeenCalledOnce(); expect(mounted.confirm).not.toHaveBeenCalled()
  } finally { mounted.unmount() }
})

test('a late reverse geocoder result cannot replace a later map click', async () => {
  let click!: (event: MapEvent) => void
  const replies: ((status: string, result: { regeocode: { formattedAddress: string } }) => void)[] = []
  const sdk = {
    Map: class { on(_event: string, handler: typeof click) { click = handler } setCenter() {} remove() {} destroy() {} },
    Marker: class { on() {} setPosition() {} }, Circle: class {},
    Geocoder: class { getAddress(_position: number[], callback: typeof replies[number]) { replies.push(callback) } },
  } as unknown as AMapSdk
  mocks.load.mockResolvedValueOnce(sdk)
  const mounted = mount()
  try {
    await Promise.resolve(); await nextTick()
    click({ lnglat: { getLng: () => 122, getLat: () => 32 } })
    click({ lnglat: { getLng: () => 123, getLat: () => 33 } })
    replies[1]!('complete', { regeocode: { formattedAddress: '新选点地址' } }); await Promise.resolve()
    replies[0]!('complete', { regeocode: { formattedAddress: '旧地址' } }); await Promise.resolve(); await nextTick()
    expect(mounted.root.textContent).toContain('新选点地址'); expect(mounted.root.textContent).not.toContain('旧地址')
    expect(mounted.confirm).not.toHaveBeenCalled()
    Array.from(mounted.root.querySelectorAll('button')).find(button => button.textContent === '确认位置')!.click()
    expect(mounted.confirm).toHaveBeenCalledWith(expect.objectContaining({ longitude: 123, latitude: 33, address: '新选点地址', coordinateSystem: 'GCJ02' }))
  } finally { mounted.unmount() }
})
