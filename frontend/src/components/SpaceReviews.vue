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
            <div class="public-reviews">
        <article v-for="review in published.items" :key="review.id" class="public-review">
          <div class="public-review-header">
            <span class="review-avatar" aria-hidden="true">{{ review.author.slice(0, 1) }}</span>
            <div class="review-author"><strong>{{ review.author }}</strong><time :datetime="review.updatedAt">{{ formatTime(review.updatedAt) }}</time></div>
            <div class="review-scores"><span>环境 <strong>{{ review.environmentScore }}</strong><small>/ 5</small></span><span>设施 <strong>{{ review.facilityScore }}</strong><small>/ 5</small></span></div>
          </div>
          <p class="review-content" :class="{ 'muted': !review.content }">{{ review.content || '未填写文字说明' }}</p>
        </article>
      </div>
      <p v-if="!published.items.length" class="muted">暂无已通过的评价。</p>
      <el-pagination v-if="published.total > 5" v-model:current-page="page" :page-size="5" :total="published.total" layout="prev, pager, next" @current-change="refresh" />
    </template>
  </section>
  <section v-if="account?.role === 'USER'" class="panel detail-panel"><h2>我的评价</h2>
    <el-alert v-if="ownError" type="error" :title="ownError" :closable="false"><el-button text @click="loadOwn()">重试</el-button></el-alert>
    <p v-if="ownLoading && !own" class="muted">正在加载本人评价…</p>
    <ReviewEditor v-if="own" :space-id="spaceId" :review="own.review" :space-enabled="own.spaceEnabled" @saved="saved" @reloaded="own = $event" />
  </section>
</template>

<style scoped>
.public-reviews { display: grid; gap: 12px; }
.public-review { border: 1px solid #e4ecea; border-radius: 10px; padding: 16px; background: #fbfdfc; }
.public-review-header { display: flex; align-items: center; gap: 10px; }
.review-avatar { display: grid; place-items: center; width: 36px; height: 36px; flex-shrink: 0; border-radius: 50%; background: #e5f3ee; color: #137d72; font-weight: 600; }
.review-author { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.review-author strong { font-size: 14px; overflow-wrap: anywhere; }
.review-author time { color: #84928f; font-size: 12px; }
.review-scores { margin-left: auto; display: flex; flex-wrap: wrap; gap: 8px; }
.review-scores > span { display: flex; align-items: baseline; gap: 5px; padding: 5px 10px; background: #eef5f2; border-radius: 6px; color: #52646b; font-size: 12px; }
.review-scores strong { color: #137d72; font-size: 16px; }
.review-scores small { font-size: 11px; color: #84928f; }
.public-review .review-content { margin: 12px 0 0; line-height: 1.7; }
@media (max-width: 600px) {
  .public-review { padding: 12px; }
  .public-review-header { flex-wrap: wrap; }
  .review-scores { width: 100%; margin-left: 0; }
}
</style>
