<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'
import type { Overview } from '../types'
import { formatTime } from '../types'
import { usePolling } from '../usePolling'
import { ElMessage } from 'element-plus'
const overview = ref<Overview | null>(null)
const busy = ref(false)
const { error, refresh } = usePolling(async signal => { overview.value = await api<Overview>('/system/overview', { signal }) })
async function control() {
  if (!overview.value) return
  busy.value = true
  try { overview.value = await api<Overview>(`/system/simulation/${overview.value.running ? 'pause' : 'resume'}`, { method: 'POST' }); ElMessage.success('模拟运行状态已更新') }
  catch (failure) { ElMessage.error(failure instanceof Error ? failure.message : '控制失败') }
  finally { busy.value = false }
}
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">服务器管理员 / SYSTEM</div><h1>服务器概况</h1><p class="muted">查看运行信息，控制服务器统一生成模拟状态。</p></div></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <template v-if="overview"><div class="overview-grid"><div class="panel"><small>学习空间</small><strong>{{ overview.spaceCount }}</strong></div><div class="panel"><small>账号总数</small><strong>{{ overview.accountCount }}</strong></div><div class="panel"><small>模拟运行</small><strong>{{ overview.running ? '运行中' : '已暂停' }}</strong></div></div>
    <section class="panel detail-panel"><h2>模拟与刷新</h2><p>暂停后不再生成新采样，已有状态会在 {{ overview.validitySeconds }} 秒有效期后显示未知。</p><el-button :type="overview.running ? 'warning' : 'primary'" :loading="busy" @click="control">{{ overview.running ? '暂停模拟' : '继续模拟' }}</el-button><dl><dt>运行编号</dt><dd>#{{ overview.simulationRunId }}</dd><dt>刷新周期</dt><dd>{{ overview.pollSeconds }} 秒</dd><dt>噪声窗口</dt><dd>{{ overview.noiseWindowSeconds }} 秒</dd><dt>服务启动时间</dt><dd>{{ formatTime(overview.startedAt) }}</dd><dt>系统版本</dt><dd>{{ overview.version }}</dd></dl></section>
  </template>
</template>
