<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api, jsonRequest } from '../api'
import type { StudySpace } from '../types'
import { ElMessage } from 'element-plus'
import { usePolling } from '../usePolling'
type Device = { id: number; deviceId: string; name: string; spaceId: number | null; spaceName: string | null; spaceAddress: string | null; enabled: boolean; version: number; lastReceivedAt: string | null }
type Candidate = { deviceId: string; firstSeenAt: string; lastSeenAt: string; online: boolean }
const devices = ref<Device[]>([])
const spaces = ref<StudySpace[]>([])
const candidates = ref<Candidate[]>([])
const saveError = ref('')
const opened = ref(false)
const saving = ref(false)
const editing = ref<number | null>(null)
const defaults = () => ({ deviceId: '', name: '', spaceId: null as number | null, enabled: true, version: null as number | null })
const form = reactive(defaults())
let revision = 0
const { error, refresh: load } = usePolling(async signal => {
  if (saving.value) return
  const requestRevision = revision
  const [deviceRows, spaceRows, candidateRows] = await Promise.all([
    api<Device[]>('/data/hardware-devices', { signal }), api<StudySpace[]>('/data/spaces', { signal }),
    api<Candidate[]>('/data/hardware-devices/candidates', { signal }),
  ])
  // 刷新只更新选项，不覆盖正在填写的表单；保存前的旧请求不得恢复已录入的候选。
  if (signal.aborted || requestRevision !== revision || saving.value) return
  devices.value = deviceRows; spaces.value = spaceRows; candidates.value = candidateRows
})
function edit(device?: Device) {
  editing.value = device?.id ?? null
  Object.assign(form, device ? { deviceId: device.deviceId, name: device.name, spaceId: device.spaceId, enabled: device.enabled, version: device.version } : defaults())
  saveError.value = ''; opened.value = true
}
async function save() {
  if (!form.deviceId) { saveError.value = '请先从下拉框选择已发现的设备'; return }
  saving.value = true; revision++
  try {
    await api('/data/hardware-devices' + (editing.value === null ? '' : '/' + editing.value), jsonRequest(editing.value === null ? 'POST' : 'PUT', { ...form, name: form.name.trim() || `签到设备 ${form.deviceId}` }))
    opened.value = false; ElMessage.success('设备及空间绑定已保存')
  } catch (failure) { saveError.value = failure instanceof Error ? failure.message : '保存失败' }
  finally { saving.value = false; void load() }
}
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">数据管理员 / DEVICES</div><h1>设备管理</h1><p class="muted">录入签到设备并绑定学习空间，保存后立即生效。</p></div><div><el-button @click="load">刷新</el-button><el-button type="primary" @click="edit()">录入设备</el-button></div></div>
  <el-alert :title="`已发现 ${candidates.length} 台待录入设备。设备正常联网上报后，点击“录入设备”即可下拉选择；列表每5秒自动刷新。`" type="info" :closable="false" />
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <div class="panel table-panel"><el-table :data="devices" stripe>
    <el-table-column prop="deviceId" label="物理设备ID" min-width="180" /><el-table-column prop="name" label="设备名称" min-width="140" />
    <el-table-column label="绑定空间 / 位置" min-width="220"><template #default="scope">{{ scope.row.spaceName ?? '未绑定' }}<div class="muted">{{ scope.row.spaceAddress }}</div></template></el-table-column>
    <el-table-column label="接收状态" min-width="190"><template #default="scope">{{ scope.row.lastReceivedAt ? '最近接收：' + new Date(scope.row.lastReceivedAt).toLocaleString() : '尚未收到上传' }}</template></el-table-column>
    <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
    <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="primary" @click="edit(scope.row)">编辑</el-button></template></el-table-column>
  </el-table></div>
  <el-dialog v-model="opened" class="cf-dialog" append-to-body :title="editing === null ? '录入设备' : '编辑设备'" width="600px" :close-on-click-modal="!saving" :show-close="!saving">
    <form class="edit-form" @submit.prevent="save">
      <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" />
      <label>选择设备<el-select v-model="form.deviceId" :disabled="editing !== null" placeholder="请选择自动发现的设备" no-data-text="尚未发现设备，请确认设备正常上传及MQTT接收已开启">
        <el-option v-if="editing !== null" :value="form.deviceId" :label="form.deviceId" />
        <el-option v-for="device in candidates" :key="device.deviceId" :value="device.deviceId" :label="`${device.deviceId} · ${device.online ? '最近在线' : '曾经发现，当前离线'}`" />
      </el-select></label>
      <el-button v-if="editing === null" text type="primary" @click="load">刷新发现列表</el-button>
      <p v-if="editing === null" class="muted">让设备联网并正常上报，再选择设备和空间。绑定前的上报不计入空间人数。</p>
      <label>设备名称（选填）<el-input v-model="form.name" maxlength="100" placeholder="留空自动使用设备ID命名" /></label>
      <label>绑定学习空间<el-select v-model="form.spaceId" clearable placeholder="请选择学习空间" @clear="form.spaceId = null"><el-option v-for="space in spaces" :key="space.id" :value="space.id" :label="`${space.id} · ${space.name} · ${space.address}`" :disabled="devices.some(device => device.spaceId === space.id && device.id !== editing)" /></el-select></label>
      <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
      <p class="muted">每个空间只能绑定一台设备。已上传数据的设备不能换绑或解绑；停用会拒绝新上传并保留历史。未绑定设备不能上传。</p>
      <el-button native-type="submit" type="primary" :loading="saving">保存</el-button>
    </form>
  </el-dialog>
</template>
