<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { api, jsonRequest } from '../api'
import type { StudySpace } from '../types'
import { facilityNames, typeNames } from '../types'
import { ElMessage } from 'element-plus'
import LocationPicker from '../components/LocationPicker.vue'
import type { MapLocation } from '../maps/types'
const spaces = ref<StudySpace[]>([])
const error = ref('')
const opened = ref(false)
const saving = ref(false)
const locationOpened = ref(false)
const manualConfirmed = ref(false)
const initialLocation = computed<MapLocation | undefined>(() => form.coordinateSystem === 'GCJ02' ? { latitude: form.latitude, longitude: form.longitude, address: form.address, coordinateSystem: 'GCJ02' } : undefined)
const editing = ref<number | null>(null)
const defaults = () => ({ name: '', type: 'STUDY_ROOM', address: '', latitude: 31.2304, longitude: 121.4737, capacity: 30, openTime: '08:00', closeTime: '22:00', openDays: [1,2,3,4,5,6,7], allDay: false, facilities: ['SEAT'], description: '', enabled: true, coordinateSystem: 'GCJ02' as StudySpace['coordinateSystem'] })
const form = reactive(defaults())
async function load() {
  try { spaces.value = await api<StudySpace[]>('/data/spaces'); error.value = '' }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败' }
}
function edit(space?: StudySpace) {
  editing.value = space?.id ?? null
  Object.assign(form, space ? { ...space, openDays: space.openDays.split(',').map(Number), facilities: space.facilities.split(',').filter(Boolean) } : defaults())
  manualConfirmed.value = false; locationOpened.value = false
  error.value = ''; opened.value = true
}
function confirmLocation(location: MapLocation) {
  form.latitude = location.latitude; form.longitude = location.longitude
  if (location.address) form.address = location.address
  form.coordinateSystem = 'GCJ02'; manualConfirmed.value = false; locationOpened.value = false
}
async function save() {
  // 旧坐标不能因修改名称等资料而被默认为高德坐标，必须显式确认。
  if (form.coordinateSystem !== 'GCJ02' && !manualConfirmed.value) {
    error.value = '请地图选点，或明确确认手动坐标为 GCJ-02'; return
  }
  saving.value = true
  try {
    await api('/data/spaces' + (editing.value === null ? '' : '/' + editing.value), jsonRequest(editing.value === null ? 'POST' : 'PUT', { ...form, coordinateSystem: 'GCJ02' }))
    opened.value = false; ElMessage.success('空间资料已保存'); await load()
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '保存失败' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">数据管理员 / SPACES</div><h1>空间管理</h1><p class="muted">维护基础资料、设施与营业时间，停用后保留历史记录。</p></div><el-button type="primary" @click="edit()">新增空间</el-button></div>
  <el-alert v-if="error && !opened" :title="error" type="error" :closable="false"><el-button text @click="load">重试</el-button></el-alert>
  <div class="panel table-panel"><el-table :data="spaces" stripe><el-table-column prop="id" label="编号" width="70" /><el-table-column prop="name" label="名称" min-width="190" /><el-table-column label="类型" width="100"><template #default="scope">{{ typeNames[scope.row.type] }}</template></el-table-column><el-table-column label="位置" width="120"><template #default="scope"><el-tag :type="scope.row.coordinateSystem === 'GCJ02' ? 'success' : 'warning'">{{ scope.row.coordinateSystem === 'GCJ02' ? 'GCJ-02' : '待确认位置' }}</el-tag></template></el-table-column><el-table-column prop="capacity" label="容量" width="80" /><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="100"><template #default="scope"><el-button text type="primary" @click="edit(scope.row)">编辑</el-button></template></el-table-column></el-table></div>
  <el-dialog v-model="opened" :title="editing === null ? '新增空间' : '编辑空间'" width="min(640px, 94vw)">
    <form class="edit-form" @submit.prevent="save">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <label>名称<el-input v-model="form.name" maxlength="100" /></label><label>地址<el-input v-model="form.address" maxlength="200" /></label>
      <label>类型<el-select v-model="form.type"><el-option v-for="(name,key) in typeNames" :key="key" :value="key" :label="name" /></el-select></label>
      <el-alert v-if="form.coordinateSystem !== 'GCJ02'" title="旧位置坐标系未知，确认前不参与附近推荐和地图展示。" type="warning" :closable="false" />
      <el-button @click="locationOpened = true">地图选择位置</el-button>
      <p class="muted">手动输入仅接受高德 GCJ-02 坐标；默认值是上海演示点，请确认真实学习空间位置。</p>
      <el-checkbox v-if="form.coordinateSystem !== 'GCJ02'" v-model="manualConfirmed">已确认手动坐标为 GCJ-02</el-checkbox>
      <div class="form-row"><label>纬度（GCJ-02）<el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="6" /></label><label>经度（GCJ-02）<el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="6" /></label><label>容量<el-input-number v-model="form.capacity" :min="1" :max="10000" /></label></div>
      <div class="form-row"><label>开放时间<input v-model="form.openTime" type="time"></label><label>关闭时间<input v-model="form.closeTime" type="time"></label><el-checkbox v-model="form.allDay">全天开放</el-checkbox></div>
      <label>开放日<el-checkbox-group v-model="form.openDays"><el-checkbox v-for="day in 7" :key="day" :value="day">周{{ day }}</el-checkbox></el-checkbox-group></label>
      <label>设施<el-checkbox-group v-model="form.facilities"><el-checkbox v-for="(name,key) in facilityNames" :key="key" :value="key">{{ name }}</el-checkbox></el-checkbox-group></label>
      <label>说明<el-input v-model="form.description" type="textarea" maxlength="1000" /></label><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
      <p class="muted">容量不能低于当前在场人数；停用后退出用户列表并停止新模拟数据。</p>
      <el-button native-type="submit" type="primary" :loading="saving">保存</el-button>
    </form>
  </el-dialog>
  <el-dialog v-model="locationOpened" title="确定学习空间位置" width="min(860px, 96vw)"><LocationPicker v-if="locationOpened" :initial="initialLocation" :allow-locate="true" @confirm="confirmLocation" @cancel="locationOpened = false" /></el-dialog>
</template>
