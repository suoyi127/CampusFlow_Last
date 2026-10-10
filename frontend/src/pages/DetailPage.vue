<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import type { SpaceCard } from '../types'
import { facilityNames, typeNames, metricIsRecent } from '../types'
import { detailOrigin } from '../filters'
import { usePolling } from '../usePolling'
import StatusMetrics from '../components/StatusMetrics.vue'
import SpaceReviews from '../components/SpaceReviews.vue'
const route = useRoute()
const card = ref<SpaceCard | null>(null)
const { error, loading, refresh, elapsedSeconds } = usePolling(async signal => {
  const origin = detailOrigin(route.query)
  card.value = await api<SpaceCard>(`/spaces/${route.params.id}?latitude=${origin.latitude}&longitude=${origin.longitude}`, { signal })
})
</script>

<template>
  <RouterLink to="/spaces" class="back-link">← 返回空间列表</RouterLink>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <div v-if="loading && !card" class="panel"><el-skeleton :rows="6" animated /></div>
  <template v-if="card"><div class="page-heading"><div><div class="eyebrow">{{ typeNames[card.space.type] }} / #{{ card.space.id }}</div><h1>{{ card.space.name }}</h1><p class="muted">{{ card.space.address }}</p></div><el-tag effect="plain">模拟状态 · 运行 #{{ card.status.simulationRunId }}</el-tag></div>
    <section class="panel detail-panel"><h2>此刻的空间</h2><StatusMetrics :status="card.status" :elapsed="elapsedSeconds" /><p class="muted">当前读数 {{ metricIsRecent(card.status, card.status.noiseUpdatedAt, card.status.noiseState, elapsedSeconds) ? `${card.status.noiseDb} dB` : '暂无近期数据' }} · 设备 {{ card.status.deviceState === 'ONLINE' ? '正常' : '离线' }} · 近期典型噪声采用配置窗口内中位数</p></section>
    <section class="panel detail-panel space-profile">
      <div class="profile-heading"><h2>空间资料</h2><el-tag size="small" effect="plain">{{ typeNames[card.space.type] }}</el-tag></div>
      <div class="profile-facts">
        <div class="profile-fact profile-hours"><span>开放时间</span><strong>{{ card.space.allDay ? '全天开放' : `${card.space.openTime} — ${card.space.closeTime}` }}</strong><small>开放日：周 {{ card.space.openDays }}</small></div>
        <div class="profile-fact"><span>空间容量</span><strong>{{ card.space.capacity }} <small>人</small></strong><small>空间可容纳人数</small></div>
        <div class="profile-fact"><span>直线距离</span><strong>{{ Math.round(card.distanceMeters) }} <small>米</small></strong><small>相对当前搜索起点</small></div>
      </div>
      <div class="profile-section"><h3>配套设施</h3><div class="facility-tags"><el-tag v-for="item in card.space.facilities.split(',').filter(Boolean)" :key="item" type="info" effect="plain">{{ facilityNames[item] || item }}</el-tag><span v-if="!card.space.facilities" class="muted">暂无设施信息</span></div></div>
      <div class="profile-section"><h3>空间说明</h3><p class="profile-description" :class="{ muted: !card.space.description }">{{ card.space.description || '暂无说明' }}</p></div>
    </section>
    <SpaceReviews :space-id="card.space.id" />
  </template>
</template>

<style scoped>
.profile-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.profile-heading h2 { margin: 0; }
.profile-facts { display: grid; grid-template-columns: minmax(0, 1.5fr) repeat(2, minmax(0, 1fr)); gap: 12px; }
.profile-fact { display: flex; flex-direction: column; gap: 8px; padding: 16px; background: #f5f8f7; border: 1px solid #e8efec; border-radius: 9px; min-width: 0; }
.profile-hours { background: #edf6f2; border-color: #d9ebe3; }
.profile-fact > span { font-size: 12px; color: #647b72; }
.profile-fact strong { font-size: 23px; color: #234c40; overflow-wrap: anywhere; }
.profile-fact small { font-size: 12px; font-weight: 400; color: #7a8b85; }
.profile-section { padding-top: 16px; margin-top: 16px; border-top: 1px solid #edf1ef; }
.profile-section h3 { font-size: 13px; color: #52646b; margin: 0 0 10px; }
.profile-description { font-size: 14px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; }
@media (max-width: 600px) {
  .profile-facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .profile-hours { grid-column: 1 / -1; }
  .profile-fact { padding: 12px; }
  .profile-fact strong { font-size: 20px; }
}
</style>
