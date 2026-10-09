<script setup lang="ts">
import { ref } from 'vue'
import { account, api } from '../api'
import type { MySpaceReview, PublicReviewPage, ReviewView } from '../reviewTypes'
import { reviewIsOlder } from '../reviewTypes'
import { formatTime } from '../types'
import { usePolling } from '../usePolling'
import ReviewSummary from './ReviewSummary.vue'
import ReviewEditor from './ReviewEditor.vue'
const props = defineProps<{ spaceId: number }>()
const published = ref<PublicReviewPage | null>(null)
const own = ref<MySpaceReview | null>(null)
const ownError = ref('')
const ownLoading = ref(false)
const page = ref(1)
const { loading, error, refresh } = usePolling(async signal => {
  published.value = await api<PublicReviewPage>(`/spaces/${props.spaceId}/reviews?page=${page.value}&pageSize=5`, { signal })
  await loadOwn(signal)
})
async function loadOwn(signal?: AbortSignal) {
  if (account.value?.role !== 'USER') return
  ownLoading.value = true
  try {
    const latest = await api<MySpaceReview>(`/user/reviews/${props.spaceId}`, { signal })
    // 保存期间启动的旧查询可能更晚返回，不能把已确认的新版本回退。
    if (!reviewIsOlder(own.value?.review ?? null, latest.review)) own.value = latest
    ownError.value = ''
  }
  catch (failure) { ownError.value = failure instanceof Error ? failure.message : '本人评价加载失败' }
  finally { ownLoading.value = false }
}
function saved(review: ReviewView) { own.value = { review, spaceEnabled: review.spaceEnabled }; void refresh() }
</script>

<template>
  <section class="panel detail-panel"><h2>用户评价</h2><el-alert v-if="error" type="error" :title="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
    <p v-if="loading && !published" class="muted">正在加载评价…</p>
    <template v-if="published"><ReviewSummary :summary="published.summary" /><p class="muted">仅展示当前已通过评价，评分独立于推荐得分。</p>
      <article v-for="review in published.items" :key="review.id" class="review-item"><div class="review-item-heading"><strong>{{ review.author }}</strong><span class="muted">{{ formatTime(review.updatedAt) }}</span></div><p>环境 {{ review.environmentScore }} / 5 · 设施 {{ review.facilityScore }} / 5</p><p class="review-content">{{ review.content || '未填写文字说明' }}</p></article>
      <el-pagination v-if="published.total > 5" v-model:current-page="page" :page-size="5" :total="published.total" layout="prev, pager, next" @current-change="refresh" />
    </template>
  </section>
  <section v-if="account?.role === 'USER'" class="panel detail-panel"><h2>我的评价</h2>
    <el-alert v-if="ownError" type="error" :title="ownError" :closable="false"><el-button text @click="loadOwn()">重试</el-button></el-alert>
    <p v-if="ownLoading && !own" class="muted">正在加载本人评价…</p>
    <ReviewEditor v-if="own" :space-id="spaceId" :review="own.review" :space-enabled="own.spaceEnabled" @saved="saved" @reloaded="own = $event" />
  </section>
</template>
