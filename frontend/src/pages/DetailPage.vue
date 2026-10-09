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
  <template v-if="card"><div class="page-heading"><div><div class="eyebrow">{{ typeNames[card.space.type] }} / #{{ card.space.id }}</div><h1>{{ card.space.name }}</h1><p class="muted">{{ card.space.address }}</p></div><el-tag effect="plain">{{ card.status.source === 'HARDWARE' ? '设备实测' : `模拟状态 · 运行 #${card.status.simulationRunId}` }}</el-tag></div>
    <section class="panel detail-panel"><h2>此刻的空间</h2><StatusMetrics :status="card.status" :elapsed="elapsedSeconds" /><p class="muted">当前读数 {{ metricIsRecent(card.status, card.status.noiseUpdatedAt, card.status.noiseState, elapsedSeconds) ? `${card.status.noiseDb} dB` : '暂无近期数据' }} · 设备 {{ card.status.deviceState === 'ONLINE' ? '正常' : '离线' }} · 近期典型噪声采用配置窗口内中位数</p></section>
    <section class="panel detail-panel"><h2>空间资料</h2><dl><dt>开放时间</dt><dd>{{ card.space.allDay ? '全天开放' : `${card.space.openTime} — ${card.space.closeTime}` }}（周 {{ card.space.openDays }}）</dd><dt>容量</dt><dd>{{ card.space.capacity }} 人</dd><dt>直线距离</dt><dd>{{ Math.round(card.distanceMeters) }} 米</dd><dt>设施</dt><dd>{{ card.space.facilities.split(',').filter(Boolean).map(item => facilityNames[item]).join('、') || '暂无' }}</dd><dt>说明</dt><dd>{{ card.space.description || '暂无说明' }}</dd></dl></section>
    <SpaceReviews :space-id="card.space.id" />
  </template>
</template>
