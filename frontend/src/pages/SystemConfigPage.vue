<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { api, ApiFailure, jsonRequest } from '../api'
import type { RuntimeConfig } from '../systemTypes'
import { usePolling } from '../usePolling'
const current = ref<RuntimeConfig | null>(null), baseline = ref<RuntimeConfig | null>(null)
const form = reactive({ simulationSeconds: 5, validitySeconds: 30, noiseWindowSeconds: 60, distanceWeight: .3, quietWeight: .3, freeWeight: .25, facilityWeight: .15 })
const busy = ref(false), saveError = ref(''), conflict = ref(false), notice = ref('')
const fields = [{ key: 'distanceWeight', name: '距离' }, { key: 'quietWeight', name: '安静' }, { key: 'freeWeight', name: '空闲座位' }, { key: 'facilityWeight', name: '设施' }] as const
const sum = computed(() => fields.reduce((total, field) => total + (form[field.key] ?? 0), 0))
function adopt(config: RuntimeConfig) {
  baseline.value = { ...config }
  if (!current.value || config.version >= current.value.version) current.value = { ...config }
  const { version: _version, ...values } = config; Object.assign(form, values)
  saveError.value = ''; conflict.value = false; notice.value = ''
}
const { error, refresh } = usePolling(async signal => {
  const config = await api<RuntimeConfig>('/system/config', { signal })
  // 新配置仅更新提示，避免轮询覆盖管理员尚未提交的参数。
  if (!current.value || config.version >= current.value.version) current.value = config
  if (!baseline.value) adopt(config)
})
async function reload() {
  busy.value = true
  try { adopt(await api<RuntimeConfig>('/system/config')) }
  catch (failure) { saveError.value = failure instanceof Error ? failure.message : '重载失败' }
  finally { busy.value = false }
}
async function save() {
  if (!baseline.value) return
  if (form.validitySeconds < 2 * form.simulationSeconds || form.noiseWindowSeconds < form.validitySeconds || Math.abs(sum.value - 1) > .000001) {
    saveError.value = '有效期至少为采样周期的两倍，噪声窗口至少为有效期，四项权重之和必须为 1'; return
  }
  busy.value = true; saveError.value = ''; notice.value = ''
  try { adopt(await api<RuntimeConfig>('/system/config', jsonRequest('PUT', { ...form, expectedVersion: baseline.value.version }))); notice.value = '配置已保存，后续采样、状态计算与推荐使用新参数' }
  catch (failure) { saveError.value = failure instanceof Error ? failure.message : '保存失败'; conflict.value = failure instanceof ApiFailure && failure.status === 409 }
  finally { busy.value = false }
}
</script>
<template>
  <div class="page-heading"><div><div class="eyebrow">服务器管理员 / CONFIGURATION</div><h1>运行配置</h1><p class="muted">调整服务器模拟周期、状态有效期与推荐权重；前端仍每 5 秒刷新。</p></div></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="refresh">重试</el-button></el-alert>
  <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" />
  <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
  <form v-if="baseline" class="panel review-editor" @submit.prevent="save"><p class="muted">编辑版本 {{ baseline.version }} · 当前服务器版本 {{ current?.version }}</p><el-alert v-if="current && current.version > baseline.version" title="服务器配置已更新。你的输入仍保留，请重新加载后再保存。" type="warning" :closable="false" />
    <h2>模拟与状态</h2><label>模拟采样周期（2—60 秒）<el-input-number v-model="form.simulationSeconds" :min="2" :max="60" :precision="0" :disabled="busy" /></label><label>状态有效期（5—300 秒）<el-input-number v-model="form.validitySeconds" :min="5" :max="300" :precision="0" :disabled="busy" /></label><label>噪声统计窗口（5—600 秒）<el-input-number v-model="form.noiseWindowSeconds" :min="5" :max="600" :precision="0" :disabled="busy" /></label><p class="muted">有效期至少为采样周期的两倍，噪声统计窗口至少为有效期。</p>
    <h2>推荐权重</h2><label v-for="field in fields" :key="field.key">{{ field.name }}<el-input-number v-model="form[field.key]" :min="0" :max="1" :step=".05" :precision="4" :disabled="busy" /></label><p>权重之和：{{ sum.toFixed(4) }}（应为 1）</p><div><el-button native-type="submit" type="primary" :loading="busy" :disabled="conflict || !!(current && current.version > baseline.version)">保存配置</el-button><el-button :disabled="busy" @click="reload">重新加载（替换输入）</el-button></div>
  </form>
</template>
