<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api, ApiFailure, jsonRequest } from '../api'
import { formatTime, roleNames } from '../types'
import type { Page, Role } from '../types'
import type { ManagedAccount } from '../systemTypes'
import { usePolling } from '../usePolling'
const result = ref<Page<ManagedAccount> | null>(null)
const filters = reactive({ username: '', role: '', enabled: '' })
const applied = ref(''), page = ref(1)
const opened = ref(false), selected = ref<ManagedAccount | null>(null)
const form = reactive({ username: '', password: '', role: 'USER' as Role, enabled: true })
const busy = ref(false), saveError = ref(''), conflict = ref(false)
const { loading, error, refresh } = usePolling(async signal => {
  result.value = await api<Page<ManagedAccount>>(`/system/accounts?${applied.value}&page=${page.value}&pageSize=20`, { signal })
})
function filter() {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) if (value !== '') query.set(key, value)
  applied.value = query.toString(); page.value = 1; void refresh()
}
function edit(row: ManagedAccount | null) {
  // 编辑快照独立于轮询列表，后台更新只能通过显式重载进入表单。
  selected.value = row ? { ...row } : null
  Object.assign(form, { username: row?.username ?? '', password: '', role: row?.role ?? 'USER', enabled: row?.enabled ?? true })
  saveError.value = ''; conflict.value = false; opened.value = true
}
async function reload() {
  const id = selected.value?.id
  if (!id) return
  busy.value = true
  try {
    edit(await api<ManagedAccount>(`/system/accounts/${id}`))
  } catch (failure) { saveError.value = failure instanceof Error ? failure.message : '重载失败' }
  finally { busy.value = false }
}
async function save() {
  if (!selected.value && (!/^[A-Za-z0-9_]{3,64}$/.test(form.username) || form.password.length < 8 || new TextEncoder().encode(form.password).length > 72)) {
    saveError.value = '用户名须为 3—64 位英文字母、数字或下划线；密码须至少 8 个字符且不超过 72 个 UTF-8 字节'; return
  }
  busy.value = true; saveError.value = ''
  try {
    if (selected.value) await api(`/system/accounts/${selected.value.id}`, jsonRequest('PUT', { role: form.role, enabled: form.enabled, expectedVersion: selected.value.version }))
    else await api('/system/accounts', jsonRequest('POST', { ...form }))
    form.password = ''; opened.value = false; await refresh()
  } catch (failure) {
    saveError.value = failure instanceof Error ? failure.message : '保存失败'
    // 重名和最后管理员约束可直接修改后重试，仅旧版本要求重载。
    conflict.value = failure instanceof ApiFailure && failure.code === 'VERSION_CONFLICT'
  }
  finally { busy.value = false }
}
</script>
<template>
  <div class="page-heading"><div><div class="eyebrow">服务器管理员 / ACCOUNTS</div><h1>账号管理</h1><p class="muted">创建账号、调整角色与启停状态；角色或状态变化将使旧登录失效。</p></div><el-button type="primary" @click="edit(null)">创建账号</el-button></div>
  <form class="panel moderation-filters" @submit.prevent="filter"><label>用户名<el-input v-model="filters.username" placeholder="包含匹配" clearable /></label><label>角色<el-select v-model="filters.role"><el-option label="全部角色" value="" /><el-option v-for="(name,key) in roleNames" :key="key" :label="name" :value="key" /></el-select></label><label>状态<el-select v-model="filters.enabled"><el-option label="全部状态" value="" /><el-option label="启用" value="true" /><el-option label="停用" value="false" /></el-select></label><el-button native-type="submit" type="primary" :loading="loading">查询</el-button></form>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <div class="panel table-panel"><el-table :data="result?.items ?? []" v-loading="loading"><el-table-column prop="username" label="用户名" min-width="160" /><el-table-column label="角色" min-width="150"><template #default="{ row }">{{ roleNames[row.role as Role] }}</template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="创建时间" min-width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }"><el-button text type="primary" @click="edit(row)">编辑</el-button></template></el-table-column></el-table></div>
  <el-pagination v-if="result" v-model:current-page="page" :total="result.total" :page-size="20" layout="total, prev, pager, next" @current-change="refresh" />
  <el-dialog class="cf-dialog" append-to-body v-model="opened" :title="selected ? '编辑账号' : '创建账号'" width="min(560px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy"><el-alert v-if="saveError" :title="saveError" type="error" :closable="false"><el-button v-if="conflict && selected" text :loading="busy" @click="reload">重载当前账号（替换输入）</el-button></el-alert><form class="review-editor" @submit.prevent="save"><label>用户名<el-input v-model="form.username" :disabled="busy || !!selected" maxlength="64" /></label><label v-if="!selected">初始密码<el-input v-model="form.password" type="password" show-password :disabled="busy" autocomplete="new-password" /><small class="muted">至少 8 个字符，不超过 72 个 UTF-8 字节；仅创建时设置。</small></label><label>角色<el-select v-model="form.role" :disabled="busy"><el-option v-for="(name,key) in roleNames" :key="key" :label="name" :value="key" /></el-select></label><label>启用<el-switch v-model="form.enabled" :disabled="busy" /></label><p class="muted">至少保留一个启用的服务器管理员。修改自身角色或停用自身后需重新登录。</p><el-button native-type="submit" type="primary" :loading="busy" :disabled="conflict">保存</el-button></form></el-dialog>
</template>
