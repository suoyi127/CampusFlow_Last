// @vitest-environment happy-dom
import { createApp, defineComponent, h, nextTick } from 'vue'
import { expect, test, vi } from 'vitest'
import SystemAccountsPage from './SystemAccountsPage.vue'
import { ApiFailure } from '../api'
const mocks = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../api', () => ({ api: mocks.api, jsonRequest: (method: string, body: unknown) => ({ method, body: JSON.stringify(body) }), ApiFailure: class extends Error { constructor(public status: number, public code: string, message: string) { super(message) } } }))
vi.mock('../usePolling', () => ({ usePolling: () => ({ error: '', loading: false, refresh: vi.fn() }) }))
test('a duplicate username preserves input and allows correction and another submission', async () => {
  const controls: { props: { modelValue: unknown }; change: (value: unknown) => void }[] = []
  const input = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], setup(props, { emit }) { controls.push({ props, change: value => emit('update:modelValue', value) }); return () => null } })
  const button = defineComponent({ inheritAttrs: false, setup: (_, { attrs, slots }) => () => h('button', { disabled: attrs.disabled, onClick: attrs.onClick as () => void }, slots.default?.()) })
  const pass = defineComponent({ inheritAttrs: false, setup: (_, { slots, attrs }) => () => h('div', [String(attrs.title ?? ''), slots.default?.()]) })
  const empty = defineComponent({ setup: () => () => null })
  const app = createApp(SystemAccountsPage)
  app.component('ElInput', input); app.component('ElButton', button)
  for (const name of ['ElDialog', 'ElAlert', 'ElSelect', 'ElSwitch', 'ElTag']) app.component(name, pass)
  for (const name of ['ElTable', 'ElTableColumn', 'ElOption', 'ElPagination']) app.component(name, empty)
  app.directive('loading', {})
  const root = document.createElement('div'); app.mount(root)
  try {
    controls[1]!.change('existing'); controls[2]!.change('Demo@123456'); await nextTick()
    mocks.api.mockRejectedValueOnce(new ApiFailure(409, 'USERNAME_EXISTS', '用户名已存在'))
    root.querySelector<HTMLFormElement>('form.review-editor')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await Promise.resolve(); await nextTick()
    expect(controls[2]!.props.modelValue).toBe('Demo@123456')
    const save = Array.from(root.querySelectorAll('button')).find(node => node.textContent === '保存')!
    expect(save.disabled).toBe(false)
    controls[1]!.change('another_user'); await nextTick()
    mocks.api.mockResolvedValueOnce({ id: 5 })
    root.querySelector<HTMLFormElement>('form.review-editor')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await Promise.resolve(); await nextTick()
    expect(mocks.api).toHaveBeenCalledTimes(2)
    expect(JSON.parse(mocks.api.mock.calls[1]![1].body).username).toBe('another_user')
  } finally { app.unmount() }
})
