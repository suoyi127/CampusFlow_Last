<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api, ApiFailure, jsonRequest } from '../api'
import type { Page } from '../types'
import { formatTime } from '../types'
import type { ReviewView } from '../reviewTypes'
import { reviewStatusNames, reviewStatusColors } from '../reviewTypes'
import { usePolling } from '../usePolling'
const result = ref<Page<ReviewView> | null>(null)
const filters = reactive({ spaceId: null as number | null, username: '', status: 'PENDING', updatedFrom: '', updatedTo: '' })
const applied = ref('status=PENDING')
const page = ref(1)
const selected = ref<ReviewView | null>(null)
const opened = ref(false)
const action = ref<'APPROVE'|'REJECT'|'REVOKE'>('APPROVE')
const reason = ref('')
const busy = ref(false)
const decisionError = ref('')
const conflict = ref(false)
const { loading, error, refresh } = usePolling(async signal => { result.value = await api<Page<ReviewView>>(`/data/reviews?${applied.value}&page=${page.value}&pageSize=10`, { signal }) })
function filter() {
  const query = new URLSearchParams()
  for (const [key,value] of Object.entries(filters)) if (value !== null && value !== undefined && value !== '') query.set(key,String(value))
  applied.value = query.toString(); page.value = 1; void refresh()
}
function inspect(review: ReviewView) {
  selected.value = { ...review }; reason.value = ''; action.value = review.status === 'APPROVED' ? 'REVOKE' : 'APPROVE'
  decisionError.value = ''; conflict.value = false; opened.value = true
}
async function decide() {
  if (!selected.value) return
  if (action.value !== 'APPROVE' && !reason.value.trim()) { decisionError.value = '驳回或撤销通过必须填写原因'; return }
  busy.value = true; decisionError.value = ''
  try {
    await api<ReviewView>(`/data/reviews/${selected.value.id}/decision`, jsonRequest('POST', { action: action.value, reason: reason.value, expectedVersion: selected.value.version }))
    opened.value = false; await refresh()
  } catch (failure) {
    decisionError.value = failure instanceof Error ? failure.message : '审核失败'
    conflict.value = failure instanceof ApiFailure && failure.status === 409
  } finally { busy.value = false }
}
function reloadQueue() { opened.value = false; void refresh() }
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">数据管理员 / MODERATION</div><h1>评价审核</h1><p class="muted">核对当前内容，审核通过后公开并参与均分。</p></div></div>
  <form class="panel moderation-filters" @submit.prevent="filter"><label>空间编号<el-input-number v-model="filters.spaceId" :min="1" :controls="false" placeholder="全部空间" /></label><label>用户名<el-input v-model="filters.username" clearable placeholder="精确匹配" /></label><label>状态<el-select v-model="filters.status"><el-option label="全部状态" value="" /><el-option v-for="(name,key) in reviewStatusNames" :key="key" :value="key" :label="name" /></el-select></label><label>更新日期从<input v-model="filters.updatedFrom" type="date"></label><label>至<input v-model="filters.updatedTo" type="date"></label><el-button native-type="submit" type="primary" :loading="loading">查询</el-button></form>
  <el-alert v-if="error" type="error" :title="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <div class="panel table-panel"><el-table :data="result?.items ?? []" stripe v-loading="loading"><el-table-column prop="spaceName" label="空间" min-width="150" /><el-table-column prop="username" label="用户" width="140" /><el-table-column label="评分" width="150"><template #default="scope">环境 {{ scope.row.environmentScore }} / 设施 {{ scope.row.facilityScore }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="reviewStatusColors[scope.row.status as keyof typeof reviewStatusColors]">{{ reviewStatusNames[scope.row.status as keyof typeof reviewStatusNames] }}</el-tag></template></el-table-column><el-table-column label="更新时间" min-width="180"><template #default="scope">{{ formatTime(scope.row.updatedAt) }}</template></el-table-column><el-table-column label="操作" width="110"><template #default="scope"><el-button text type="primary" @click="inspect(scope.row)">查看 / 审核</el-button></template></el-table-column></el-table></div>
  <el-pagination v-if="result" v-model:current-page="page" :total="result.total" :page-size="10" layout="total, prev, pager, next" @current-change="refresh" />
  <el-dialog class="cf-dialog" append-to-body v-model="opened" title="评价内容与审核" width="min(640px,94vw)"><template v-if="selected"><h3>{{ selected.spaceName }} · {{ selected.username }}</h3><p>环境 {{ selected.environmentScore }} / 5 · 设施 {{ selected.facilityScore }} / 5</p><p class="review-content">{{ selected.content || '未填写文字说明' }}</p><p class="muted">版本 {{ selected.version }} · 更新 {{ formatTime(selected.updatedAt) }}</p><p v-if="selected.reviewedAt" class="muted">最近审核：{{ selected.reviewerName }} · {{ formatTime(selected.reviewedAt) }} · {{ selected.reviewReason || '通过' }}</p>
      <el-alert v-if="decisionError" :title="decisionError" type="error" :closable="false"><el-button v-if="conflict" text @click="reloadQueue">返回列表重新加载</el-button></el-alert>
      <form v-if="selected.status === 'PENDING' || selected.status === 'APPROVED'" class="review-editor" @submit.prevent="decide"><label>审核动作<el-select v-model="action" :disabled="busy || conflict"><template v-if="selected.status === 'PENDING'"><el-option label="通过" value="APPROVE" /><el-option label="驳回" value="REJECT" /></template><el-option v-else label="撤销通过并驳回" value="REVOKE" /></el-select></label><label>原因（驳回或撤销必填）<el-input v-model="reason" type="textarea" maxlength="500" show-word-limit :disabled="busy || conflict" /></label><el-button native-type="submit" type="primary" :loading="busy" :disabled="conflict">确认审核</el-button></form><p v-else class="muted">该状态仅可查看；用户修改后重新进入待审核。</p>
    </template></el-dialog>
</template>
