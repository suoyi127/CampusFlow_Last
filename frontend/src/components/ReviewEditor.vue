<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api, ApiFailure, jsonRequest } from '../api'
import type { MySpaceReview, ReviewView } from '../reviewTypes'
import { reviewStatusColors, reviewStatusNames, reviewIsOlder } from '../reviewTypes'
import { formatTime } from '../types'
const props = defineProps<{ spaceId: number; review: ReviewView | null; spaceEnabled: boolean }>()
const emit = defineEmits<{ saved: [review: ReviewView]; reloaded: [result: MySpaceReview] }>()
const form = reactive({ environmentScore: 0, facilityScore: 0, content: '' })
const busy = ref(false)
const error = ref('')
const conflict = ref(false)
const notice = ref('')
const baseline = ref<ReviewView | null>(null)
const forceReload = ref(false)
const dirty = computed(() => form.environmentScore !== (baseline.value?.environmentScore ?? 0)
  || form.facilityScore !== (baseline.value?.facilityScore ?? 0) || form.content !== (baseline.value?.content ?? ''))
watch(() => props.review, review => {
  if (reviewIsOlder(baseline.value, review)) return
  if (dirty.value && !forceReload.value) {
    if (review?.version !== baseline.value?.version) { error.value = '评价已有新版本，请重载后再提交；当前输入已保留。'; conflict.value = true }
    return
  }
  baseline.value = review ? { ...review } : null; forceReload.value = false
  form.environmentScore = review?.environmentScore ?? 0; form.facilityScore = review?.facilityScore ?? 0; form.content = review?.content ?? ''
}, { immediate: true })
function failed(failure: unknown) {
  error.value = failure instanceof Error ? failure.message : '操作失败，请重试'
  conflict.value = failure instanceof ApiFailure && failure.status === 409
}
async function submit() {
  if (!form.environmentScore || !form.facilityScore) { error.value = '请分别选择环境和设施评分'; return }
  busy.value = true; error.value = ''; notice.value = ''; conflict.value = false
  try {
    const review = await api<ReviewView>(`/user/reviews/${props.spaceId}`, jsonRequest('PUT', { ...form, expectedVersion: baseline.value?.version ?? 0 }))
    forceReload.value = true
    emit('saved', review); notice.value = '评价已提交，等待审核。'
  } catch (failure) { failed(failure) }
  finally { busy.value = false }
}
async function withdraw() {
  if (!props.review) return
  busy.value = true; error.value = ''; notice.value = ''; conflict.value = false
  try {
    const review = await api<ReviewView>(`/user/reviews/${props.spaceId}/withdraw`, jsonRequest('POST', { expectedVersion: baseline.value?.version ?? 0 }))
    forceReload.value = true
    emit('saved', review); notice.value = '评价已撤回，不再公开或参与均分。'
  } catch (failure) { failed(failure) }
  finally { busy.value = false }
}
async function reload() {
  busy.value = true
  try { const latest = await api<MySpaceReview>(`/user/reviews/${props.spaceId}`); forceReload.value = true; emit('reloaded', latest); error.value = ''; conflict.value = false; notice.value = '' }
  catch (failure) { failed(failure) }
  finally { busy.value = false }
}
</script>

<template>
  <form class="review-editor" @submit.prevent="submit">
    <div v-if="review" class="review-status"><el-tag :type="reviewStatusColors[review.status]">{{ reviewStatusNames[review.status] }}</el-tag><span class="muted">更新于 {{ formatTime(review.updatedAt) }}</span></div>
    <el-alert v-if="review?.reviewedAt" type="info" :closable="false" :title="`最近一次审核：${formatTime(review.reviewedAt)}${review.reviewReason ? ' · ' + review.reviewReason : ''}`" />
    <el-alert v-if="!spaceEnabled" type="warning" title="空间已停用，保留历史评价；可撤回，不能提交或修改。" :closable="false" />
    <el-alert v-if="error" type="error" :title="error" :closable="false"><el-button v-if="conflict" text :disabled="busy" @click="reload">重新加载（替换当前输入）</el-button></el-alert>
    <el-alert v-if="notice" type="success" :title="notice" :closable="false" />
    <label>环境评分<el-rate v-model="form.environmentScore" :disabled="busy || !spaceEnabled" show-score score-template="{value} 分" /></label>
    <label>设施评分<el-rate v-model="form.facilityScore" :disabled="busy || !spaceEnabled" show-score score-template="{value} 分" /></label>
    <label>评价说明（选填）<el-input v-model="form.content" type="textarea" :rows="4" maxlength="500" show-word-limit :disabled="busy || !spaceEnabled" placeholder="说说环境与设施的实际感受" /></label>
    <p class="muted">审核通过后公开。修改已通过评价会重新待审核，并立即退出原均分。</p>
    <div class="review-actions"><el-button native-type="submit" type="primary" :loading="busy" :disabled="!spaceEnabled || conflict">{{ review ? '修改并重新提交' : '提交评价' }}</el-button><el-button v-if="review && review.status !== 'WITHDRAWN'" type="danger" plain :disabled="busy || conflict" @click="withdraw">撤回评价</el-button></div>
  </form>
</template>
