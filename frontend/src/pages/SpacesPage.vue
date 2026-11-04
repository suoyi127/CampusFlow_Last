<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { api } from '../api'
import type { Page, SpaceCard } from '../types'
import { facilityNames, typeNames, recommendationIsStale } from '../types'
import { filters, queryString } from '../filters'
import { usePolling } from '../usePolling'
import StatusMetrics from '../components/StatusMetrics.vue'
import ReviewSummary from '../components/ReviewSummary.vue'
import LocationPicker from '../components/LocationPicker.vue'
import SpaceMap from '../components/SpaceMap.vue'
import type { MapLocation } from '../maps/types'
const result = ref<Page<SpaceCard> | null>(null)
const locationOpened = ref(false)
const view = ref<'list' | 'map'>('list')
const locationNote = ref(filters.latitude === 31.2304 && filters.longitude === 121.4737 ? '上海演示起点（非真实校园）' : '保留上次确认的出发点')
const origin = computed<MapLocation>(() => ({ latitude: filters.latitude, longitude: filters.longitude, coordinateSystem: 'GCJ02', address: locationNote.value }))
const detailLink = (id: number) => ({ path: `/spaces/${id}`, query: { latitude: String(filters.latitude), longitude: String(filters.longitude) } })
let originVersion = 0
let retry: ReturnType<typeof setTimeout> | undefined
let active = true
const { error, loading, refresh, elapsedSeconds } = usePolling(async signal => {
  const version = originVersion
  try {
    const response = await api<Page<SpaceCard>>('/spaces?' + queryString(), { signal })
    if (active && version === originVersion) result.value = response
  } catch (failure) { if (version === originVersion) throw failure }
  finally {
    // 起点改变时旧请求仍可能在途；丢弃旧距离，并等轮询释放 pending 后立即重查。
    if (active && version !== originVersion) retry = setTimeout(() => { if (active) void refresh() }, 0)
  }
})
function refreshOrigin() { originVersion++; result.value = null; void refresh() }
onUnmounted(() => { active = false; clearTimeout(retry) })
function confirmLocation(location: MapLocation) {
  filters.latitude = location.latitude; filters.longitude = location.longitude
  locationNote.value = location.address || '已确认地图起点'
  locationOpened.value = false; refreshOrigin()
}
</script>

<template>
  <div class="discovery-page">
  <div class="page-heading"><div><div class="eyebrow">学习空间 / DISCOVER</div><h1>给专注找个好去处</h1><p class="muted">按你的需要筛选，比较此刻的校园学习空间。</p></div><el-tag effect="plain">服务器模拟 · 每 5 秒刷新</el-tag></div>
  <div class="search-layout">
    <div class="filter-column"><form class="panel filters" @submit.prevent="refresh"><div class="filter-fields">
      <h3>筛选条件</h3>


      <label>最大直线距离（米）<el-input-number v-model="filters.maxDistance" :min="0" :max="1000000" :step="500" /></label>
      <label class="filter-half">最低安静等级<el-select v-model="filters.minQuiet" clearable placeholder="不限"><el-option v-for="level in 5" :key="level" :value="level" :label="`${level} 级及以上`" /></el-select></label>
      <label class="filter-half">最高拥挤率<el-select v-model="filters.maxOccupancy" clearable placeholder="不限"><el-option v-for="value in [0.4, 0.7, 0.9, 1]" :key="value" :value="value" :label="`${value * 100}%`" /></el-select></label>
      <label>必需设施（同时满足）<el-select v-model="filters.facilities" multiple collapse-tags collapse-tags-tooltip :max-collapse-tags="1" placeholder="不限设施"><el-option v-for="(label, key) in facilityNames" :key="key" :value="key" :label="label" /></el-select></label>
      <label>预计开始时间<input v-model="filters.startAt" type="datetime-local"></label>
      <label>预计学习时长（分钟）<el-input-number v-model="filters.durationMinutes" :min="0" :max="1440" :step="30" /></label>
      <el-checkbox v-model="filters.openOnly">仅显示当前开放</el-checkbox>
      <small class="muted">预计时间仅检查营业时段，不保证未来空位。</small>
      <div class="location-controls"><span>{{ locationNote }}</span></div>
      </div></form><div class="filter-actions"><el-button @click="locationOpened = true" title="定位 / 地图选点">定位</el-button><el-button type="primary" :loading="loading" @click="refresh">应用条件</el-button></div>
    </div>
    <section class="results">
      <form class="results-search" @submit.prevent="refresh"><label>空间名称<el-input v-model="filters.name" placeholder="搜索名称" clearable /></label><label>空间类型<el-select v-model="filters.type"><el-option label="全部类型" value="" /><el-option v-for="(label, key) in typeNames" :key="key" :label="label" :value="key" /></el-select></label><el-button native-type="submit" type="primary" :loading="loading">搜索</el-button></form>
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button><span>保留筛选条件，过期指标显示未知。</span></el-alert>
      <el-alert v-for="warning in result?.warnings" :key="warning" :title="warning" type="info" :closable="false" />
      <div class="results-toolbar"><span>找到 <strong>{{ result?.total ?? '—' }}</strong> 个空间</span><el-select v-model="filters.sort" aria-label="排序方式" @change="refresh"><el-option label="综合推荐" value="SCORE" /><el-option label="距离最近" value="DISTANCE" /><el-option label="最安静" value="QUIET" /><el-option label="最不拥挤" value="OCCUPANCY" /><el-option label="设施最完善" value="FACILITY" /></el-select></div>
      <p class="muted">当前展示 {{ result?.items.length ?? 0 }} / {{ result?.total ?? '—' }} 个结果（最多 100 个），距离为直线距离。</p>
      <el-radio-group v-model="view" aria-label="展示方式"><el-radio-button value="list">列表</el-radio-button><el-radio-button value="map">地图</el-radio-button></el-radio-group>
      <div v-if="loading && !result" class="panel"><el-skeleton :rows="5" animated /></div>
      <el-empty v-else-if="result && !result.total" description="没有满足全部条件的空间" />
      <SpaceMap v-if="view === 'map' && result" :cards="result.items" :origin="origin" :elapsed="elapsedSeconds" />
      <template v-if="view === 'list'">
      <article v-for="card in result?.items" :key="card.space.id" class="panel space-card">
        <div class="card-heading"><div><span class="space-type">{{ typeNames[card.space.type] }}</span><RouterLink :to="detailLink(card.space.id)"><h2>{{ card.space.name }}</h2></RouterLink><p class="muted">{{ card.space.address }} · {{ Math.round(card.distanceMeters) }} 米</p></div><el-tag :type="card.openNow ? 'success' : 'info'">{{ card.openNow ? '开放中' : '未开放' }}</el-tag></div>
        <StatusMetrics :status="card.status" :elapsed="elapsedSeconds" />
        <ReviewSummary :summary="card.reviewSummary" />
        <div class="card-bottom"><div class="facility-tags"><el-tag v-for="item in card.space.facilities.split(',').filter(Boolean)" :key="item" type="info" size="small">{{ facilityNames[item] }}</el-tag></div><RouterLink :to="detailLink(card.space.id)">查看详情 →</RouterLink></div>
        <p class="card-reason">{{ error || recommendationIsStale(card.status, elapsedSeconds) ? '状态等待刷新，推荐理由和得分暂不可用。' : card.reasons.join(' · ') }}<span v-if="!error && !recommendationIsStale(card.status, elapsedSeconds)"> · 综合 {{ card.score.toFixed(1) }} 分</span></p>
      </article>
      </template>
    </section>
  </div>
  <el-dialog class="cf-dialog cf-dialog--map" append-to-body v-model="locationOpened" title="选择搜索起点" width="min(860px, 96vw)"><LocationPicker v-if="locationOpened" :initial="origin" :allow-locate="true" @confirm="confirmLocation" @cancel="locationOpened = false" /></el-dialog>
  </div>
</template>

<style scoped>
.discovery-page .filters { display: flex; flex-direction: column; padding: 14px; }
.discovery-page .filter-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: start; gap: 10px; }
.discovery-page .filter-fields > * { grid-column: 1 / -1; min-width: 0; }
.discovery-page .filter-fields > .filter-half { grid-column: auto; }
.discovery-page .filters label { gap: 5px; font-size: 12px; }
.discovery-page .filters h3 { font-size: 15px; margin: 0; }
.discovery-page .filters :deep(.el-select) { width: 100%; }
.discovery-page .filter-fields > small { font-size: 11px; }
.discovery-page .filters input[type=datetime-local] { padding: 6px 8px; min-width: 0; }
.discovery-page .filters .location-controls { font-size: 11px; }
.discovery-page .filter-fields > .el-checkbox { display: inline-flex; flex-direction: row; align-items: center; justify-content: center; gap: 0; height: 24px; margin: 0; }
.filter-column { display: flex; flex-direction: column; min-height: 0; gap: 12px; }
.filter-column > .filters { flex: 1; min-height: 0; }
.filter-column > .filter-actions { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 3fr); gap: 8px; padding: 12px; background: #fff; border: 1px solid #e4ecea; border-radius: 10px; box-shadow: 0 4px 16px #20332b0d; flex: none; }
.results-search { display: grid; grid-template-columns: minmax(0, 2fr) minmax(140px, 1fr) auto; align-items: end; gap: 12px; position: sticky; top: 0; z-index: 2; flex: none; }
.results-search { background: #f4f7f7; padding: 0 0 12px; border-bottom: 1px solid #e4ecea; }
.results-search label { min-width: 0; }
.results-search :deep(.el-select) { width: 100%; }
.results-search > .el-button { margin: 0; }
@media (max-width: 1100px) {
  .results-search { position: static; }
}
@media (max-width: 600px) {
  .results-search { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 10px; }
  .results-search > .el-button { grid-column: 1 / -1; }
}
/* 桌面只滚动结果区；筛选项超出高度时独立滚动，确保底部按钮仍可访问。 */
@media (min-width: 1101px) {
  .discovery-page { height: calc(100dvh - var(--header-height, 64px) - 86px); display: flex; flex-direction: column; min-height: 0; }
  .discovery-page > .page-heading { flex: none; }
  .discovery-page > .search-layout { flex: 1; min-height: 0; align-items: stretch; }
  .discovery-page .filters, .discovery-page .results { min-height: 0; overflow-y: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
  .discovery-page .results { padding-right: 4px; }
}
</style>
