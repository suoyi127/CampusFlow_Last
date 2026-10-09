<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'
import type { Page, SpaceCard } from '../types'
import { facilityNames, typeNames, recommendationIsStale } from '../types'
import { filters, queryString } from '../filters'
import { usePolling } from '../usePolling'
import StatusMetrics from '../components/StatusMetrics.vue'
import ReviewSummary from '../components/ReviewSummary.vue'
const result = ref<Page<SpaceCard> | null>(null)
const locating = ref(false)
const locationNote = ref('默认出发点：图书馆东门')
const { error, loading, refresh, elapsedSeconds } = usePolling(async signal => {
  result.value = await api<Page<SpaceCard>>('/spaces?' + queryString(), { signal })
})
function locate() {
  if (!navigator.geolocation) { locationNote.value = '定位不可用，可继续使用预置出发点'; return }
  locating.value = true
  navigator.geolocation.getCurrentPosition(position => {
    filters.latitude = position.coords.latitude; filters.longitude = position.coords.longitude
    locationNote.value = '已使用当前位置'; locating.value = false; void refresh()
  }, () => { locationNote.value = '定位失败，可继续选择预置出发点'; locating.value = false }, { timeout: 8000 })
}
function preset() { filters.latitude = 31.2304; filters.longitude = 121.4737; locationNote.value = '默认出发点：图书馆东门'; void refresh() }
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">学习空间 / DISCOVER</div><h1>给专注找个好去处</h1><p class="muted">按你的需要筛选，比较此刻的校园学习空间。</p></div><el-tag effect="plain">服务器模拟 · 每 5 秒刷新</el-tag></div>
  <div class="search-layout">
    <form class="panel filters" @submit.prevent="refresh">
      <h3>筛选条件</h3>
      <label>空间名称<el-input v-model="filters.name" placeholder="搜索名称" clearable /></label>
      <label>空间类型<el-select v-model="filters.type"><el-option label="全部类型" value="" /><el-option v-for="(label, key) in typeNames" :key="key" :label="label" :value="key" /></el-select></label>
      <label>最大直线距离（米）<el-input-number v-model="filters.maxDistance" :min="0" :max="1000000" :step="500" /></label>
      <label>最低安静等级<el-select v-model="filters.minQuiet" clearable placeholder="不限"><el-option v-for="level in 5" :key="level" :value="level" :label="`${level} 级及以上`" /></el-select></label>
      <label>最高拥挤率<el-select v-model="filters.maxOccupancy" clearable placeholder="不限"><el-option v-for="value in [0.4, 0.7, 0.9, 1]" :key="value" :value="value" :label="`${value * 100}%`" /></el-select></label>
      <label>必需设施（同时满足）<el-checkbox-group v-model="filters.facilities"><el-checkbox v-for="(label, key) in facilityNames" :key="key" :value="key">{{ label }}</el-checkbox></el-checkbox-group></label>
      <el-checkbox v-model="filters.openOnly">仅显示当前开放</el-checkbox>
      <label>预计开始时间<input v-model="filters.startAt" type="datetime-local"></label>
      <label>预计学习时长（分钟）<el-input-number v-model="filters.durationMinutes" :min="0" :max="1440" :step="30" /></label>
      <small class="muted">预计时间仅检查营业时段，不保证未来空位。</small>
      <div class="location-controls"><span>{{ locationNote }}</span><el-button :loading="locating" @click="locate">定位</el-button><el-button @click="preset">预置点</el-button></div>
      <el-button native-type="submit" type="primary" :loading="loading">应用条件</el-button>
    </form>
    <section class="results">
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button><span>保留筛选条件，过期指标显示未知。</span></el-alert>
      <el-alert v-for="warning in result?.warnings" :key="warning" :title="warning" type="info" :closable="false" />
      <div class="results-toolbar"><span>找到 <strong>{{ result?.total ?? '—' }}</strong> 个空间</span><el-select v-model="filters.sort" aria-label="排序方式" @change="refresh"><el-option label="综合推荐" value="SCORE" /><el-option label="距离最近" value="DISTANCE" /><el-option label="最安静" value="QUIET" /><el-option label="最不拥挤" value="OCCUPANCY" /><el-option label="设施最完善" value="FACILITY" /></el-select></div>
      <div v-if="loading && !result" class="panel"><el-skeleton :rows="5" animated /></div>
      <el-empty v-else-if="result && !result.total" description="没有满足全部条件的空间" />
      <article v-for="card in result?.items" :key="card.space.id" class="panel space-card">
        <div class="card-heading"><div><span class="space-type">{{ typeNames[card.space.type] }}</span><RouterLink :to="`/spaces/${card.space.id}`"><h2>{{ card.space.name }}</h2></RouterLink><p class="muted">{{ card.space.address }} · {{ Math.round(card.distanceMeters) }} 米</p></div><el-tag :type="card.openNow ? 'success' : 'info'">{{ card.openNow ? '开放中' : '未开放' }}</el-tag></div>
        <StatusMetrics :status="card.status" :elapsed="elapsedSeconds" />
        <ReviewSummary :summary="card.reviewSummary" />
        <div class="card-bottom"><div class="facility-tags"><el-tag v-for="item in card.space.facilities.split(',').filter(Boolean)" :key="item" type="info" size="small">{{ facilityNames[item] }}</el-tag></div><RouterLink :to="`/spaces/${card.space.id}`">查看详情 →</RouterLink></div>
        <p class="card-reason">{{ error || recommendationIsStale(card.status, elapsedSeconds) ? '状态等待刷新，推荐理由和得分暂不可用。' : card.reasons.join(' · ') }}<span v-if="!error && !recommendationIsStale(card.status, elapsedSeconds)"> · 综合 {{ card.score.toFixed(1) }} 分</span></p>
      </article>
    </section>
  </div>
</template>
