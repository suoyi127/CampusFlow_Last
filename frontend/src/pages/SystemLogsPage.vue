<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api } from '../api'
import { formatTime } from '../types'
import type { Page } from '../types'
import type { SystemLog } from '../systemTypes'
import { usePolling } from '../usePolling'
const result = ref<Page<SystemLog> | null>(null), page = ref(1), applied = ref('')
const filters = reactive({ actor: '', action: '', updatedFrom: '', updatedTo: '' })
const { loading, error, refresh } = usePolling(async signal => { result.value = await api<Page<SystemLog>>(`/system/logs?${applied.value}&page=${page.value}&pageSize=20`, { signal }) })
function filter() {
  if (filters.updatedFrom && filters.updatedTo && filters.updatedFrom > filters.updatedTo) return
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value)
  applied.value = query.toString(); page.value = 1; void refresh()
}
</script>
<template>
  <div class="page-heading"><div><div class="eyebrow">服务器管理员 / AUDIT</div><h1>操作日志</h1><p class="muted">只读查看业务操作记录，日期筛选及时间按北京时间显示。</p></div></div>
  <form class="panel moderation-filters" @submit.prevent="filter"><label>操作人<el-input v-model="filters.actor" placeholder="精确匹配" clearable /></label><label>动作<el-input v-model="filters.action" placeholder="如 ACCOUNT_CREATE" clearable /></label><label>日期从<input v-model="filters.updatedFrom" type="date"></label><label>至<input v-model="filters.updatedTo" type="date"></label><el-button native-type="submit" type="primary" :loading="loading">查询</el-button><p v-if="filters.updatedFrom && filters.updatedTo && filters.updatedFrom > filters.updatedTo" class="muted">开始日期不能晚于结束日期。</p></form>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <div class="panel table-panel"><el-table :data="result?.items ?? []" stripe v-loading="loading"><el-table-column label="时间" min-width="185"><template #default="{ row }">{{ formatTime(row.occurredAt) }}</template></el-table-column><el-table-column prop="actor" label="操作人" min-width="130" /><el-table-column prop="action" label="动作" min-width="180" /><el-table-column prop="target" label="目标" min-width="160" show-overflow-tooltip /><el-table-column prop="result" label="结果" width="110" /><el-table-column prop="reason" label="说明" min-width="220" show-overflow-tooltip /></el-table></div>
  <el-pagination v-if="result" v-model:current-page="page" :total="result.total" :page-size="20" layout="total, prev, pager, next" @current-change="refresh" />
</template>
