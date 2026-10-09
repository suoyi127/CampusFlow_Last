<script setup lang="ts">
import { computed } from 'vue'
import type { SpaceStatus } from '../types'
import { formatTime, metricIsRecent } from '../types'
const props = defineProps<{ status: SpaceStatus; elapsed: number }>()
function recent(time: string | null, state: string) {
  return metricIsRecent(props.status, time, state, props.elapsed)
}
const peopleValid = computed(() => recent(props.status.peopleUpdatedAt, props.status.peopleState))
const noiseValid = computed(() => props.status.deviceState === 'ONLINE' && recent(props.status.noiseUpdatedAt, props.status.noiseState))
</script>

<template>
  <div class="metrics">
    <div><small>当前人数</small><strong>{{ peopleValid ? `${status.currentPeople} / ${status.capacity}` : '暂无近期数据' }}</strong><span>{{ peopleValid ? `拥挤率 ${Math.round((status.occupancyRate ?? 0) * 100)}%` : '人数未知' }}</span></div>
    <div><small>近期典型噪声</small><strong>{{ noiseValid && status.typicalNoiseDb !== null ? `${status.typicalNoiseDb.toFixed(1)} dB` : '暂无近期数据' }}</strong><span>{{ status.deviceState === 'OFFLINE' ? '设备离线' : noiseValid ? `安静等级 ${status.quietLevel ?? '未知'} / 5` : '噪声未知' }}</span></div>
  </div>
  <div class="timestamps"><span>人数更新 {{ formatTime(status.peopleUpdatedAt) }}</span><span>噪声更新 {{ formatTime(status.noiseUpdatedAt) }}</span></div>
</template>
