<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api, jsonRequest } from '../api'
import type { NoiseDevice, Overview, Scenario } from '../types'
import { formatTime, scenarioNames } from '../types'
import { usePolling } from '../usePolling'
import { ElMessage } from 'element-plus'
const overview = ref<Overview | null>(null), devices = ref<NoiseDevice[]>([])
const busy = ref(false), resetOpened = ref(false)
const form = reactive<{ scenario: Scenario; spaceId: number | undefined; seed: number; durationSeconds: number }>({ scenario: 'NORMAL', spaceId: undefined, seed: 127, durationSeconds: 60 })
let revision = 0
const { error, refresh } = usePolling(async signal => {
  if (busy.value) return
  const requestRevision = revision
  const [state, equipment] = await Promise.all([api<Overview>('/system/overview', { signal }), api<NoiseDevice[]>('/system/devices', { signal })])
  if (requestRevision !== revision || signal.aborted || busy.value) return
  overview.value = state; devices.value = equipment
})
async function change(path: string, options: RequestInit, message: string) {
  busy.value = true; revision++
  try {
    overview.value = await api<Overview>(path, options)
    resetOpened.value = false; ElMessage.success(message)
    devices.value = await api<NoiseDevice[]>('/system/devices')
  } catch (failure) { ElMessage.error(failure instanceof Error ? failure.message : '操作失败') }
  finally { busy.value = false; void refresh() }
}
function control() {
  if (overview.value) void change(`/system/simulation/${overview.value.running ? 'pause' : 'resume'}`, { method: 'POST' }, '模拟运行状态已更新')
}
function scenario() {
  if ((form.scenario === 'NOISE_EVENT' || form.scenario === 'DEVICE_OFFLINE') && !form.spaceId) { ElMessage.error('请选择目标空间'); return }
  void change('/system/simulation/scenario', jsonRequest('PUT', form), '场景已切换；人数与噪声在下一模拟周期更新')
}
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">服务器管理员 / SYSTEM</div><h1>服务器概况与模拟</h1><p class="muted">控制服务器统一模拟，切换场景并管理检测仪状态。</p></div></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <template v-if="overview">
    <div class="overview-grid"><div class="panel"><small>学习空间</small><strong>{{ overview.spaceCount }}</strong></div><div class="panel"><small>账号总数</small><strong>{{ overview.accountCount }}</strong></div><div class="panel"><small>模拟运行</small><strong>{{ overview.running ? '运行中' : '已暂停' }}</strong></div></div>
    <section class="panel detail-panel"><h2>模拟与刷新</h2><p>暂停后停止生成采样，已有状态会在 {{ overview.validitySeconds }} 秒有效期后显示未知；营业结束仍清退到访。</p><div class="form-row"><el-button :type="overview.running ? 'warning' : 'primary'" :loading="busy" @click="control">{{ overview.running ? '暂停模拟' : '继续模拟' }}</el-button><el-button type="danger" plain :disabled="busy" @click="resetOpened = true">重置模拟</el-button></div><dl><dt>运行编号</dt><dd>#{{ overview.simulationRunId }}</dd><dt>当前场景</dt><dd>{{ scenarioNames[overview.scenario] }} · 种子 {{ overview.seed }}<span v-if="overview.targetSpaceId"> · 目标空间 #{{ overview.targetSpaceId }}</span></dd><dt v-if="overview.eventEndsAt">噪声事件截止</dt><dd v-if="overview.eventEndsAt">{{ formatTime(overview.eventEndsAt) }}，到期后停止叠加噪声</dd><dt>模拟采样周期</dt><dd>{{ overview.simulationSeconds }} 秒</dd><dt>前端刷新周期</dt><dd>{{ overview.pollSeconds }} 秒</dd><dt>噪声窗口</dt><dd>{{ overview.noiseWindowSeconds }} 秒</dd><dt>服务启动时间</dt><dd>{{ formatTime(overview.startedAt) }}</dd><dt>系统版本</dt><dd>{{ overview.version }}</dd></dl><RouterLink to="/system/config">修改运行周期、有效期及推荐权重</RouterLink></section>
    <section class="panel detail-panel"><h2>固定种子场景</h2><form class="edit-form" @submit.prevent="scenario"><div class="form-row"><label>场景<el-select v-model="form.scenario" :disabled="busy"><el-option v-for="(name,key) in scenarioNames" :key="key" :label="name" :value="key" /></el-select></label><label>随机种子<el-input-number v-model="form.seed" :min="0" :max="2147483647" :precision="0" :disabled="busy" /></label><label v-if="form.scenario === 'NOISE_EVENT'">持续秒数<el-input-number v-model="form.durationSeconds" :min="5" :max="3600" :precision="0" :disabled="busy" /></label><label v-if="form.scenario === 'NOISE_EVENT' || form.scenario === 'DEVICE_OFFLINE'">目标空间<el-select v-model="form.spaceId" placeholder="选择启用空间" :disabled="busy"><el-option v-for="device in devices.filter(d => d.enabled)" :key="device.spaceId" :label="device.spaceName" :value="device.spaceId" /></el-select></label></div><p class="muted">人流高峰逐步增加到访，噪声事件叠加 30 dB。切换场景先恢复所有设备在线，再应用目标设备离线；不清除历史读数，不改变运行开关。相同初始空间资料、时间条件、种子和操作顺序可复现结果。</p><el-button native-type="submit" type="primary" :loading="busy">应用场景</el-button></form></section>
    <section class="panel detail-panel"><h2>模拟检测仪</h2><p class="muted">离线立即使噪声未知；恢复在线按原采样时间判断有效性，下一周期产生新读数。</p><el-table :data="devices" stripe><el-table-column prop="spaceName" label="空间" min-width="170" /><el-table-column prop="deviceCode" label="设备编号" min-width="150" /><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ONLINE' ? 'success' : 'warning'">{{ scope.row.status === 'ONLINE' ? '正常' : '离线' }}</el-tag></template></el-table-column><el-table-column label="操作" width="130"><template #default="scope"><el-button text :disabled="busy" @click="change('/system/devices/' + scope.row.spaceId, jsonRequest('PUT', { online: scope.row.status !== 'ONLINE' }), '设备状态已更新')">{{ scope.row.status === 'ONLINE' ? '设为离线' : '恢复在线' }}</el-button></template></el-table-column></el-table></section>
  </template>
  <el-dialog class="cf-dialog" append-to-body v-model="resetOpened" title="重置当前模拟" width="min(520px,94vw)" :close-on-click-modal="!busy" :show-close="!busy"><p>清除本轮虚拟到访、人数快照和噪声读数，开启新运行，恢复正常场景、种子 127 和设备在线，重新生成初始到访。</p><p>账号、空间、评价、运行配置、操作日志和运行历史保留。保持当前运行或暂停状态。</p><el-button type="danger" :loading="busy" @click="change('/system/simulation/reset', { method: 'POST' }, '模拟已重置，业务数据和配置已保留')">确认重置</el-button></el-dialog>
</template>
