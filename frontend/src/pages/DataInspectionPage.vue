<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api, jsonRequest } from '../api'
import type { DeviceStatus, Page, RecordKind, HardwareRecord } from '../types'
import { formatTime } from '../types'
import { usePolling } from '../usePolling'
import { ElMessage } from 'element-plus'

const devices = ref<DeviceStatus[]>([])
const boundDevices = computed(() => devices.value.filter(device => device.spaceId !== null))
const result = ref<(Page<HardwareRecord>) | null>(null)
const filters = reactive<{ kind: RecordKind; spaceId: number | undefined; validity: string; page: number }>({ kind: 'PEOPLE', spaceId: undefined, validity: 'ALL', page: 1 })
const opened = ref(false), saving = ref(false), reason = ref('')
const selected = ref<{ kind: RecordKind; row: HardwareRecord } | null>(null)
const mutationError = ref('')
let revision = 0
const { error, loading, refresh } = usePolling(async signal => {
  if (saving.value) return
  const requestRevision = revision
  const query = new URLSearchParams({ kind: filters.kind, page: String(filters.page), pageSize: '20' })
  if (filters.spaceId !== undefined) query.set('spaceId', String(filters.spaceId))
  if (filters.kind !== 'VISIT' && filters.validity !== 'ALL') query.set('valid', filters.validity)
  const [records, equipment] = await Promise.all([
    api<Page<HardwareRecord>>('/data/records?' + query, { signal }),
    api<DeviceStatus[]>('/data/devices', { signal }),
  ])
  // 条件切换或写操作后，不允许先前请求覆盖当前列表。
  if (requestRevision !== revision || signal.aborted || saving.value) return
  result.value = records; devices.value = equipment
})
watch(() => [filters.kind, filters.spaceId, filters.validity], () => { filters.page = 1; revision++; result.value = null; void refresh() })
watch(() => filters.page, () => { revision++; result.value = null; void refresh() })
function edit(row: HardwareRecord) {
  selected.value = { kind: filters.kind, row }; reason.value = ''; mutationError.value = ''; opened.value = true
}
async function save() {
  if (saving.value) return
  if (!selected.value || selected.value.kind === 'VISIT' || !reason.value.trim()) { mutationError.value = '请填写操作原因'; return }
  saving.value = true; revision++
  try {
    const { kind, row } = selected.value
    await api(`/data/records/${kind}/${row.id}/validity`, jsonRequest('PUT', { valid: !row.valid, reason: reason.value.trim() }))
    opened.value = false; result.value = null; ElMessage.success(row.valid ? '记录已标记无效，汇总将排除它' : '记录已恢复，汇总将重新计算')
  } catch (failure) { mutationError.value = failure instanceof Error ? failure.message : '操作失败' }
  finally { saving.value = false; void refresh() }
}
function spaceName(id: number) { return devices.value.find(device => device.spaceId === id)?.spaceName ?? `空间 #${id}` }
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">数据管理员 / INSPECTION</div><h1>实时数据检查</h1><p class="muted">查看实际设备的人数、噪声和签到签退记录。</p></div><el-button :loading="loading" @click="refresh">刷新</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <section class="panel detail-panel"><h2>硬件设备</h2><el-table :data="devices" stripe><el-table-column prop="spaceName" label="空间" min-width="160" /><el-table-column prop="deviceId" label="设备编号" min-width="160" /><el-table-column label="启停状态" width="100"><template #default="scope">{{ scope.row.enabled ? '启用' : '停用' }}</template></el-table-column><el-table-column label="设备状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ONLINE' ? 'success' : 'warning'">{{ scope.row.status === 'ONLINE' ? '正常' : '离线' }}</el-tag></template></el-table-column><el-table-column label="最近接收" min-width="160"><template #default="{ row }">{{ formatTime(row.lastReceivedAt) }}</template></el-table-column></el-table></section>
  <section class="panel detail-panel">
    <div class="form-row inspection-filters"><label>记录类型<el-select v-model="filters.kind"><el-option label="人数记录" value="PEOPLE" /><el-option label="噪声读数" value="NOISE" /><el-option label="签到签退" value="VISIT" /></el-select></label><label>空间<el-select v-model="filters.spaceId" clearable placeholder="全部空间"><el-option v-for="device in boundDevices" :key="device.id" :label="device.spaceName ?? device.name" :value="device.spaceId" /></el-select></label><label v-if="filters.kind !== 'VISIT'">有效标记<el-select v-model="filters.validity"><el-option label="全部记录" value="ALL" /><el-option label="有效" value="true" /><el-option label="无效" value="false" /></el-select></label></div>
    <p class="muted">{{ result ? `${result.total} 条记录 · 查询于 ${formatTime(result.calculatedAt)}` : '等待当前条件的记录' }}</p>
    <el-table v-loading="loading && !result" :data="result?.items ?? []" stripe empty-text="暂无记录">
      <el-table-column prop="id" label="编号" width="90" /><el-table-column label="空间" min-width="150"><template #default="scope">{{ spaceName(scope.row.spaceId) }}</template></el-table-column>
      <template v-if="filters.kind === 'VISIT'">
        <el-table-column prop="deviceId" label="设备编号" min-width="180" />
        <el-table-column prop="eventId" label="事件编号" min-width="110" />
        <el-table-column label="事件" width="90"><template #default="{ row }">{{ row.eventType === 'CHECK_IN' ? '签到' : row.eventType === 'CHECK_OUT' ? '签退' : '未知' }}</template></el-table-column>
        <el-table-column prop="headcount" label="事件后人数" width="105" />
        <el-table-column label="设备时间" min-width="170"><template #default="{ row }">{{ formatTime(row.sampledAt) }}</template></el-table-column>
      </template>
      <template v-else><el-table-column :prop="filters.kind === 'PEOPLE' ? 'currentPeople' : 'noiseDb'" :label="filters.kind === 'PEOPLE' ? '人数' : '噪声 dB'" width="100" /><el-table-column label="采样时间" min-width="180"><template #default="scope">{{ formatTime(scope.row.sampledAt) }}</template></el-table-column><el-table-column label="标记" width="90"><template #default="scope"><el-tag :type="scope.row.valid ? 'success' : 'danger'">{{ scope.row.valid ? '有效' : '无效' }}</el-tag></template></el-table-column><el-table-column prop="invalidReason" label="异常原因" min-width="160" /><el-table-column label="操作" width="120"><template #default="scope"><el-button text :type="scope.row.valid ? 'danger' : 'primary'" :disabled="saving" @click="edit(scope.row)">{{ scope.row.valid ? '标记无效' : '恢复记录' }}</el-button></template></el-table-column></template>
    </el-table>
    <el-pagination v-if="result" v-model:current-page="filters.page" :total="result.total" :page-size="20" layout="prev, pager, next" />
    <p class="muted">恢复有效标记不会刷新采样时间。签到签退事件为只读。</p>
  </section>
  <el-dialog class="cf-dialog" append-to-body v-model="opened" :title="selected?.row.valid ? '标记异常记录' : '恢复误标记录'" width="min(520px,94vw)" :close-on-click-modal="!saving" :show-close="!saving">
    <form class="edit-form" @submit.prevent="save"><p>记录 #{{ selected?.row.id }}。此操作仅修改对应指标的有效标记。</p><el-alert v-if="mutationError" :title="mutationError" type="error" :closable="false" /><label>操作原因<el-input v-model="reason" type="textarea" maxlength="500" show-word-limit :disabled="saving" /></label><el-button native-type="submit" type="primary" :loading="saving">确认{{ selected?.row.valid ? '标记' : '恢复' }}</el-button></form>
  </el-dialog>
</template>
