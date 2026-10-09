// @vitest-environment happy-dom
import { createApp, defineComponent, h, nextTick, reactive } from 'vue'
import { expect, test, vi } from 'vitest'
import ReviewEditor from './ReviewEditor.vue'
import type { ReviewView } from '../reviewTypes'
import { reviewIsOlder } from '../reviewTypes'

vi.mock('../api', () => ({ api: vi.fn(), jsonRequest: vi.fn(), ApiFailure: class extends Error {} }))

const review = (version: number, content = '原评价') => ({ id: 1, version, content, environmentScore: 4,
  facilityScore: 3, status: 'PENDING', updatedAt: '2026-10-09T02:00:00Z' } as ReviewView)

function mountEditor(initial: ReviewView | null) {
  const controls: { props: { modelValue?: unknown }; change: (value: unknown) => void }[] = []
  const control = defineComponent({ inheritAttrs: false, props: ['modelValue'], emits: ['update:modelValue'], setup(props, { emit }) {
    controls.push({ props, change: value => emit('update:modelValue', value) }); return () => null
  } })
  const passthrough = defineComponent({ inheritAttrs: false, setup: (_, { slots }) => () => slots.default?.() })
  const state = reactive<{ review: ReviewView | null }>({ review: initial })
  const app = createApp({ render: () => h(ReviewEditor, { spaceId: 1, review: state.review, spaceEnabled: true }) })
  for (const name of ['ElTag', 'ElAlert', 'ElButton']) app.component(name, passthrough)
  app.component('ElRate', control); app.component('ElInput', control)
  app.mount(document.createElement('div'))
  return { state, controls, unmount: () => app.unmount() }
}

test('polling preserves unsaved text and ratings', async () => {
  const mounted = mountEditor(review(1))
  try {
    mounted.controls[0]!.change(5); mounted.controls[2]!.change('正在输入')
    await nextTick(); mounted.state.review = review(1); await nextTick()
    expect(mounted.controls[0]!.props.modelValue).toBe(5)
    expect(mounted.controls[2]!.props.modelValue).toBe('正在输入')
  } finally { mounted.unmount() }
})

test('a delayed older response cannot roll back a clean saved form', async () => {
  const mounted = mountEditor(review(2, '新评价'))
  try {
    mounted.state.review = review(1); await nextTick()
    expect(mounted.controls[2]!.props.modelValue).toBe('新评价')
    mounted.state.review = null; await nextTick()
    expect(mounted.controls[0]!.props.modelValue).toBe(4)
    expect(mounted.controls[2]!.props.modelValue).toBe('新评价')
  } finally { mounted.unmount() }
})

test('the owner response gate rejects delayed null and lower versions but accepts moderation', () => {
  expect(reviewIsOlder(review(2), null)).toBe(true)
  expect(reviewIsOlder(review(2), review(1))).toBe(true)
  expect(reviewIsOlder(review(2), review(3))).toBe(false)
  expect(reviewIsOlder(null, review(1))).toBe(false)
})
