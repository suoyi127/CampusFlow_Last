<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { api, jsonRequest } from '../api'
import type { NoiseDevice, Page, RecordKind, SimulationRecord } from '../types'
import { formatTime } from '../types'
import { usePolling } from '../usePolling'
import { ElMessage } from 'element-plus'

const devices = ref<NoiseDevice[]>([])
const result = ref<(Page<SimulationRecord> & { simulationRunId: number }) | null>(null)
const filters = reactive<{ kind: RecordKind; spaceId: number | undefined; validity: string; page: number }>({ kind: 'PEOPLE', spaceId: undefined, validity: 'ALL', page: 1 })
const opened = ref(false), saving = ref(false), reason = ref('')
const selected = ref<{ kind: RecordKind; row: SimulationRecord } | null>(null)
const mutationError = ref('')
let revision = 0
const { error, loading, refresh } = usePolling(async signal => {
  if (saving.value) return
  const requestRevision = revision
  const query = new URLSearchParams({ kind: filters.kind, page: String(filters.page), pageSize: '20' })
  if (filters.spaceId !== undefined) query.set('spaceId', String(filters.spaceId))
  if (filters.kind !== 'VISIT' && filters.validity !== 'ALL') query.set('valid', filters.validity)
  const [records, equipment] = await Promise.all([
    api<Page<SimulationRecord> & { simulationRunId: number }>('/data/records?' + query, { signal }),
    api<NoiseDevice[]>('/data/devices', { signal }),
  ])
  // 条件切换或写操作后，不允许先前请求覆盖当前列表。
  if (requestRevision !== revision || signal.aborted || saving.value) return
  if (result.value && result.value.simulationRunId !== records.simulationRunId) {
    opened.value = false; selected.value = null
    ElMessage.info('模拟已重置，已切换到新一轮记录')
  }
  result.value = records; devices.value = equipment
})
watch(() => [filters.kind, filters.spaceId, filters.validity], () => { filters.page = 1; revision++; result.value = null; void refresh() })
watch(() => filters.page, () => { revision++; result.value = null; void refresh() })
function edit(row: SimulationRecord) {
  selected.value = { kind: filters.kind, row }; reason.value = ''; mutationError.value = ''; opened.value = true
}
async function save() {
  if (!selected.value || !reason.value.trim()) { mutationError.value = '请填写操作原因'; return }
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
  <div class="page-heading"><div><div class="eyebrow">数据管理员 / INSPECTION</div><h1>实时数据检查</h1><p class="muted">检查本轮人数快照、噪声读数和虚拟到访。异常标记影响汇总，不改写到访事件。</p></div><el-button :loading="loading" @click="refresh">刷新</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <section class="panel detail-panel"><h2>模拟检测仪</h2><el-table :data="devices" stripe><el-table-column prop="spaceName" label="空间" min-width="160" /><el-table-column prop="deviceCode" label="设备编号" min-width="160" /><el-table-column label="空间状态" width="100"><template #default="scope">{{ scope.row.enabled ? '启用' : '停用' }}</template></el-table-column><el-table-column label="设备状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ONLINE' ? 'success' : 'warning'">{{ scope.row.status === 'ONLINE' ? '正常' : '离线' }}</el-tag></template></el-table-column><el-table-column prop="baseDb" label="基础噪声 dB" width="120" /><el-table-column prop="fluctuationDb" label="波动 dB" width="100" /></el-table></section>
  <section class="panel detail-panel">
    <div class="form-row inspection-filters"><label>记录类型<el-select v-model="filters.kind"><el-option label="人数快照" value="PEOPLE" /><el-option label="噪声读数" value="NOISE" /><el-option label="虚拟到访" value="VISIT" /></el-select></label><label>空间<el-select v-model="filters.spaceId" clearable placeholder="全部空间"><el-option v-for="device in devices" :key="device.spaceId" :label="device.spaceName" :value="device.spaceId" /></el-select></label><label v-if="filters.kind !== 'VISIT'">有效标记<el-select v-model="filters.validity"><el-option label="全部记录" value="ALL" /><el-option label="有效" value="true" /><el-option label="无效" value="false" /></el-select></label></div>
    <p class="muted">{{ result ? `运行 #${result.simulationRunId} · ${result.total} 条记录 · 查询于 ${formatTime(result.calculatedAt)}` : '等待当前条件的记录' }}</p>
    <el-table :data="result?.items ?? []" stripe empty-text="暂无记录">
      <el-table-column prop="id" label="编号" width="90" /><el-table-column label="空间" min-width="150"><template #default="scope">{{ spaceName(scope.row.spaceId) }}</template></el-table-column>
      <template v-if="filters.kind === 'VISIT'"><el-table-column prop="virtualPersonId" label="虚拟人员" min-width="160" /><el-table-column label="签到时间" min-width="180"><template #default="scope">{{ formatTime(scope.row.checkedInAt) }}</template></el-table-column><el-table-column label="签退时间" min-width="180"><template #default="scope">{{ scope.row.checkedOutAt ? formatTime(scope.row.checkedOutAt) : '在场' }}</template></el-table-column></template>
      <template v-else><el-table-column :prop="filters.kind === 'PEOPLE' ? 'currentPeople' : 'noiseDb'" :label="filters.kind === 'PEOPLE' ? '人数' : '噪声 dB'" width="100" /><el-table-column label="采样时间" min-width="180"><template #default="scope">{{ formatTime(scope.row.sampledAt) }}</template></el-table-column><el-table-column label="标记" width="90"><template #default="scope"><el-tag :type="scope.row.valid ? 'success' : 'danger'">{{ scope.row.valid ? '有效' : '无效' }}</el-tag></template></el-table-column><el-table-column prop="invalidReason" label="异常原因" min-width="160" /><el-table-column label="操作" width="120"><template #default="scope"><el-button text :type="scope.row.valid ? 'danger' : 'primary'" :disabled="saving" @click="edit(scope.row)">{{ scope.row.valid ? '标记无效' : '恢复记录' }}</el-button></template></el-table-column></template>
    </el-table>
    <el-pagination v-if="result" v-model:current-page="filters.page" :total="result.total" :page-size="20" layout="prev, pager, next" />
    <p class="muted">有效标记与数据时效分别判断。恢复旧记录不会刷新采样时间；新快照会继续按真实模拟到访生成。</p>
  </section>
  <el-dialog class="cf-dialog" append-to-body v-model="opened" :title="selected?.row.valid ? '标记异常记录' : '恢复误标记录'" width="min(520px,94vw)" :close-on-click-modal="!saving" :show-close="!saving">
    <form class="edit-form" @submit.prevent="save"><p>记录 #{{ selected?.row.id }}。操作后会重新选择有效快照或计算噪声中位数。</p><el-alert v-if="mutationError" :title="mutationError" type="error" :closable="false" /><label>操作原因<el-input v-model="reason" type="textarea" maxlength="500" show-word-limit :disabled="saving" /></label><el-button native-type="submit" type="primary" :loading="saving">确认{{ selected?.row.valid ? '标记' : '恢复' }}</el-button></form>
  </el-dialog>
</template>
