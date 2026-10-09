<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { loadAMap } from '../maps/amap'
import { callbackRequest, LatestSelection, validLocation } from '../maps/location'
import type { AMapMap, AMapMarker, AMapSdk, MapLocation, Poi } from '../maps/types'
const props = withDefaults(defineProps<{ initial?: MapLocation; allowLocate?: boolean }>(), { allowLocate: true })
const emit = defineEmits<{ confirm: [point: MapLocation]; cancel: [] }>()
const container = ref<HTMLElement>()
const draft = ref<MapLocation | null>(props.initial ? { ...props.initial } : null)
const manual = reactive({ longitude: props.initial?.longitude ?? 121.4737, latitude: props.initial?.latitude ?? 31.2304 })
const keyword = ref(''), results = ref<Poi[]>([]), error = ref(''), note = ref(''), loading = ref(false), searching = ref(false), locating = ref(false)
const selection = new LatestSelection()
let map: AMapMap | undefined, marker: AMapMarker | undefined, circle: unknown, sdk: AMapSdk | undefined, disposed = false
function draw(point: MapLocation, center = true) {
  if (!map || !sdk) return
  if (!marker) {
    marker = new sdk.Marker({ map, position: [point.longitude, point.latitude], draggable: true })
    marker.on('dragend', event => void pick({ longitude: event.lnglat.getLng(), latitude: event.lnglat.getLat(), coordinateSystem: 'GCJ02' }))
  } else marker.setPosition([point.longitude, point.latitude])
  if (circle) { map.remove(circle); circle = undefined }
  if (point.accuracy && point.accuracy > 0) circle = new sdk.Circle({ map, center: [point.longitude, point.latitude], radius: point.accuracy, fillOpacity: .12, strokeColor: '#207e70' })
  if (center) map.setCenter([point.longitude, point.latitude])
}
async function init() {
  if (loading.value || map) return
  loading.value = true; error.value = ''
  try {
    const loaded = await loadAMap()
    if (disposed || !container.value) return
    sdk = loaded; map = new sdk.Map(container.value, { center: [manual.longitude, manual.latitude], zoom: 16 })
    map.on('click', event => void pick({ longitude: event.lnglat.getLng(), latitude: event.lnglat.getLat(), coordinateSystem: 'GCJ02' }))
    if (draft.value) draw(draft.value)
  } catch (failure) { if (!disposed) error.value = failure instanceof Error ? failure.message : '地图加载失败' }
  finally { if (!disposed) loading.value = false }
}
async function pick(point: MapLocation) {
  if (!validLocation(point)) { error.value = '请输入合法的 GCJ-02 经纬度'; return }
  const token = selection.begin(); locating.value = false; searching.value = false
  draft.value = { ...point }; manual.longitude = point.longitude; manual.latitude = point.latitude
  results.value = []; note.value = ''; draw(point)
  if (!sdk || point.address) return
  try {
    const geocoder = new sdk.Geocoder({ extensions: 'base' })
    const address = await callbackRequest<string>((done, fail) => geocoder.getAddress([point.longitude, point.latitude], (status, result) => {
      if (status === 'complete' && result.regeocode) done(result.regeocode.formattedAddress)
      else fail('地址查询未成功，仍可确认坐标并手动填写地址')
    }))
    if (selection.isCurrent(token) && draft.value) draft.value = { ...draft.value, address }
  } catch (failure) { if (selection.isCurrent(token)) note.value = failure instanceof Error ? failure.message : '地址查询失败' }
}
async function search() {
  if (!sdk || !keyword.value.trim()) return
  const token = selection.begin(); searching.value = true; locating.value = false; note.value = ''
  try {
    const pois = await callbackRequest<Poi[]>((done, fail) => new sdk!.PlaceSearch({ pageSize: 8, extensions: 'base' }).search(keyword.value.trim(), (status, result) => {
      if (status === 'complete') done(result.poiList?.pois ?? [])
      else if (status === 'no_data') done([])
      else fail('地点搜索失败，请检查配置或改为地图选点')
    }))
    if (selection.isCurrent(token)) { results.value = pois.filter(poi => poi.location); if (!results.value.length) note.value = '没有搜索结果，可换关键词或点击地图' }
  } catch (failure) { if (selection.isCurrent(token)) note.value = failure instanceof Error ? failure.message : '搜索失败' }
  finally { if (selection.isCurrent(token)) searching.value = false }
}
function choose(poi: Poi) { void pick({ longitude: poi.location.getLng(), latitude: poi.location.getLat(), address: [poi.name, poi.address].filter(Boolean).join(' · '), coordinateSystem: 'GCJ02' }) }
async function locate() {
  if (!sdk) return
  const token = selection.begin(); locating.value = true; searching.value = false; note.value = ''
  try {
    const position = await callbackRequest<MapLocation>((done, fail) => new sdk!.Geolocation({ enableHighAccuracy: true, timeout: 8000, convert: true, needAddress: true, showButton: false, showMarker: false, showCircle: false }).getCurrentPosition((status, result) => {
      if (status !== 'complete' || !result.position) { fail('定位失败或权限被拒绝，可搜索地点、点击地图或使用手动坐标'); return }
      done({ longitude: result.position.getLng(), latitude: result.position.getLat(), coordinateSystem: 'GCJ02',
        address: result.formattedAddress, accuracy: Number.isFinite(result.accuracy) && result.accuracy! > 0 ? result.accuracy : undefined })
    }))
    if (selection.isCurrent(token)) await pick(position)
  } catch (failure) { if (selection.isCurrent(token)) note.value = failure instanceof Error ? failure.message : '定位失败' }
  finally { if (selection.isCurrent(token)) locating.value = false }
}
function confirm() { if (draft.value && validLocation(draft.value)) emit('confirm', { ...draft.value }) }
onMounted(init)
onUnmounted(() => { disposed = true; selection.close(); map?.destroy(); map = undefined })
</script>
<template>
  <section class="location-picker">
    <p class="muted">搜索地点、点击地图或拖动标记。只有确认位置后才应用；地图拖动不会自动选择中心。</p>
    <el-alert v-if="error" :title="error" type="warning" :closable="false"><el-button text :loading="loading" @click="init">重试地图</el-button></el-alert>
    <div class="map-search"><el-input v-model="keyword" placeholder="输入学校、楼宇或地址" :disabled="!sdk" @keyup.enter="search" /><el-button :loading="searching" :disabled="!sdk" @click="search">搜索</el-button><el-button v-if="allowLocate" :disabled="!sdk" :loading="locating" @click="locate">使用当前位置</el-button></div>
    <p v-if="allowLocate" class="muted">点击定位才申请权限。位置仅用于本次选点，不持续跟踪；手机浏览器定位通常需要 HTTPS。</p>
    <ul v-if="results.length" class="map-search-results"><li v-for="(poi,index) in results" :key="poi.id ?? index"><button type="button" @click="choose(poi)">{{ poi.name }} {{ poi.address }}</button></li></ul>
    <div ref="container" class="amap-canvas" :aria-busy="loading" aria-label="位置选择地图" />
    <p v-if="note" role="status">{{ note }}</p>
    <p v-if="draft">已选：{{ draft.address || '未取得地址，可手动填写' }} · {{ draft.longitude.toFixed(6) }}, {{ draft.latitude.toFixed(6) }}<span v-if="draft.accuracy"> · 定位精度约 {{ Math.round(draft.accuracy) }} 米，请核对精度圈</span></p>
    <details><summary>手动输入 GCJ-02 坐标（地图不可用时仍可使用）</summary><div class="form-row"><label>经度<el-input-number v-model="manual.longitude" :min="-180" :max="180" :precision="6" /></label><label>纬度<el-input-number v-model="manual.latitude" :min="-90" :max="90" :precision="6" /></label><el-button @click="pick({ ...manual, coordinateSystem: 'GCJ02' })">选择手动坐标</el-button></div><small>请先确认坐标来自高德 GCJ-02；GPS 原始坐标不能直接使用。</small></details>
    <div class="review-actions"><el-button type="primary" :disabled="!draft" @click="confirm">确认位置</el-button><el-button @click="emit('cancel')">取消</el-button></div>
  </section>
</template>
