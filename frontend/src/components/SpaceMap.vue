<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { SpaceCard } from '../types'
import type { AMapMap, AMapSdk, MapLocation } from '../maps/types'
import { loadAMap } from '../maps/amap'
import StatusMetrics from './StatusMetrics.vue'
import ReviewSummary from './ReviewSummary.vue'
const props = defineProps<{ cards: SpaceCard[]; origin: MapLocation; elapsed: number }>()
const container = ref<HTMLElement>(), error = ref(''), loading = ref(false), selectedId = ref<number | null>(null)
const selected = computed(() => props.cards.find(card => card.space.id === selectedId.value))
let sdk: AMapSdk | undefined, map: AMapMap | undefined, markers: unknown[] = [], disposed = false, fitted = false
function content(text: string, className: string) { const node = document.createElement('span'); node.className = className; node.textContent = text; return node }
function draw() {
  if (!sdk || !map) return
  map.remove(markers); markers = []
  markers.push(new sdk.Marker({ map, position: [props.origin.longitude, props.origin.latitude], content: content('出发点', 'map-origin-marker'), zIndex: 120 }))
  props.cards.filter(card => card.space.coordinateSystem === 'GCJ02').forEach((card, index) => {
    // 业务名称只经 Vue/textContent 输出，不能拼进 SDK 的 HTML 信息窗。
    const marker = new sdk!.Marker({ map, position: [card.space.longitude, card.space.latitude], title: card.space.name, content: content(String(index + 1), 'map-space-marker') })
    marker.on('click', () => { selectedId.value = card.space.id }); markers.push(marker)
  })
  if (!fitted) { map.setFitView(markers); fitted = true }
}
async function init() {
  if (loading.value || map) return
  loading.value = true; error.value = ''
  try {
    const loaded = await loadAMap()
    if (disposed || !container.value) return
    sdk = loaded; map = new sdk.Map(container.value, { center: [props.origin.longitude, props.origin.latitude], zoom: 15 }); draw()
  } catch (failure) { if (!disposed) error.value = failure instanceof Error ? failure.message : '地图暂不可用，请使用列表' }
  finally { if (!disposed) loading.value = false }
}
watch(() => props.cards, draw)
watch(() => [props.origin.longitude, props.origin.latitude], () => { fitted = false; draw() })
onMounted(init)
onUnmounted(() => { disposed = true; map?.destroy(); map = undefined; markers = [] })
</script>
<template>
  <section class="panel space-map">
    <el-alert v-if="error" :title="error" type="warning" :closable="false"><el-button text :loading="loading" @click="init">重试地图</el-button></el-alert>
    <p class="muted">地图展示与列表相同的查询结果。点击编号查看空间；移动地图不会改变出发点。距离为直线距离。</p>
    <div ref="container" class="amap-canvas" :aria-busy="loading" aria-label="学习空间地图" />
    <article v-if="selected" class="map-space-summary"><h2>{{ selected.space.name }}</h2><p>{{ selected.space.address }} · 直线距离 {{ Math.round(selected.distanceMeters) }} 米</p><StatusMetrics :status="selected.status" :elapsed="elapsed" /><ReviewSummary :summary="selected.reviewSummary" /><RouterLink :to="{ path: `/spaces/${selected.space.id}`, query: { latitude: origin.latitude, longitude: origin.longitude } }">查看详情 →</RouterLink></article>
  </section>
</template>
