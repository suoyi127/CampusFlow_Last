<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'
import type { Page } from '../types'
import { formatTime } from '../types'
import type { MySpaceReview, ReviewView } from '../reviewTypes'
import { reviewStatusNames, reviewStatusColors } from '../reviewTypes'
import { usePolling } from '../usePolling'
import ReviewEditor from '../components/ReviewEditor.vue'
const result = ref<Page<ReviewView> | null>(null)
const filter = ref('')
const page = ref(1)
const opened = ref(false)
const selected = ref<ReviewView | null>(null)
const { loading, error, refresh } = usePolling(async signal => {
  result.value = await api<Page<ReviewView>>(`/user/reviews?status=${filter.value}&page=${page.value}&pageSize=10`, { signal })
})
function edit(review: ReviewView) { selected.value = { ...review }; opened.value = true }
function saved(review: ReviewView) { selected.value = review; void refresh() }
function reloaded(value: MySpaceReview) { selected.value = value.review }
function changeFilter() { page.value = 1; void refresh() }
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">用户 / MY REVIEWS</div><h1>我的评价</h1><p class="muted">管理你对学习空间的评分，查看审核状态与原因。</p></div><el-select v-model="filter" class="review-filter" @change="changeFilter"><el-option label="全部状态" value="" /><el-option v-for="(name,key) in reviewStatusNames" :key="key" :value="key" :label="name" /></el-select></div>
  <el-alert v-if="error" type="error" :title="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <p v-if="loading && !result" class="muted">正在加载…</p><el-empty v-if="result && !result.total" description="此状态下暂无评价，可前往空间详情提交" />
  <article v-for="review in result?.items" :key="review.id" class="panel detail-panel"><div class="review-item-heading"><h2>{{ review.spaceName }}<small v-if="!review.spaceEnabled" class="muted"> · 已停用</small></h2><el-tag :type="reviewStatusColors[review.status]">{{ reviewStatusNames[review.status] }}</el-tag></div><p>环境 {{ review.environmentScore }} / 5 · 设施 {{ review.facilityScore }} / 5</p><p class="review-content">{{ review.content || '未填写文字说明' }}</p><p v-if="review.reviewReason" class="review-reason">最近审核原因：{{ review.reviewReason }}</p><div class="review-item-heading"><span class="muted">更新 {{ formatTime(review.updatedAt) }}</span><el-button @click="edit(review)">{{ review.spaceEnabled ? '修改 / 管理' : '查看 / 撤回' }}</el-button></div></article>
  <el-pagination v-if="result && result.total > 10" v-model:current-page="page" :page-size="10" :total="result.total" layout="prev, pager, next" @current-change="refresh" />
  <el-dialog class="cf-dialog" append-to-body v-model="opened" :title="selected?.spaceName + ' · 我的评价'" width="min(620px,94vw)"><ReviewEditor v-if="opened && selected" :space-id="selected.spaceId" :review="selected" :space-enabled="selected.spaceEnabled" @saved="saved" @reloaded="reloaded" /></el-dialog>
</template>
