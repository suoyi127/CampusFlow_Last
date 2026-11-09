<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'
import { formatTime, type DeviceStatus, type Overview } from '../types'
import { usePolling } from '../usePolling'

const overview = ref<Overview | null>(null)
const devices = ref<DeviceStatus[]>([])
const { error, loading, refresh } = usePolling(async signal => {
  const [state, equipment] = await Promise.all([
    api<Overview>('/system/overview', { signal }),
    api<DeviceStatus[]>('/system/devices', { signal }),
  ])
  if (!signal.aborted) {
    overview.value = state
    devices.value = equipment
  }
})
</script>

<template>
  <div class="page-heading">
    <div><div class="eyebrow">服务器管理员 / SYSTEM</div><h1>服务器概况</h1></div>
    <el-button :loading="loading" @click="refresh">刷新</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <el-skeleton v-else-if="!overview" animated :rows="4" />
  <template v-if="overview">
    <div class="overview-grid">
      <div class="panel"><small>学习空间</small><strong>{{ overview.spaceCount }}</strong></div>
      <div class="panel"><small>账号总数</small><strong>{{ overview.accountCount }}</strong></div>
      <div class="panel"><small>在线设备 / 总数</small><strong>{{ overview.onlineDeviceCount }} / {{ overview.deviceCount }}</strong></div>
    </div>
    <section class="panel detail-panel">
      <h2>运行状态</h2>
      <dl>
        <dt>服务启动时间</dt><dd>{{ formatTime(overview.startedAt) }}</dd>
        <dt>系统版本</dt><dd>{{ overview.version }}</dd>
        <dt>指标有效期</dt><dd>{{ overview.validitySeconds }} 秒</dd>
        <dt>噪声统计窗口</dt><dd>{{ overview.noiseWindowSeconds }} 秒</dd>
      </dl>
      <RouterLink to="/system/config">运行配置</RouterLink>
    </section>
    <section class="panel detail-panel">
      <h2>硬件设备</h2>
      <el-table :data="devices" stripe empty-text="暂无设备">
        <el-table-column prop="name" label="设备名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="deviceId" label="设备编号" min-width="175" />
        <el-table-column label="绑定空间" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.spaceName ?? '未绑定' }}</template>
        </el-table-column>
        <el-table-column label="设备状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'">
              {{ !row.enabled ? '停用' : row.status === 'ONLINE' ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近接收" min-width="165">
          <template #default="{ row }">{{ formatTime(row.lastReceivedAt) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </template>
</template>
