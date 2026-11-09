<script setup lang="ts">
import { ref } from 'vue'
import { login, logout, account } from '../api'
import { adminOnly, homeForRole } from '../webMode'
import { router } from '../router'
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
async function submit() {
  if (!username.value || !password.value) { error.value = '请输入账号和密码'; return }
  busy.value = true; error.value = ''
  try {
    await login(username.value, password.value)
    password.value = ''
    if (adminOnly && account.value?.role === 'USER') {
      await logout()
      error.value = '请使用数据管理员或服务器管理员账号'
      return
    }
    await router.replace(homeForRole(account.value?.role))
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '登录失败' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="login-layout">
    <div v-if="adminOnly" class="login-copy"><div class="eyebrow">CampusFlow</div><h1>校园空间管理</h1><p>维护空间与设备，查看实际运行数据。</p></div>
    <div v-else class="login-copy"><div class="eyebrow">找到你的学习节奏</div><h1>下一段专注，<br>从合适的空间开始。</h1><p>距离、安静程度、空闲座位与设施，<br>在一个页面里轻松比较。</p><div class="login-feature">01 / 选择条件　02 / 比较空间　03 / 开始学习</div></div>
    <form class="panel login-form" @submit.prevent="submit"><h2>欢迎回来</h2><p class="muted">{{ adminOnly ? '请使用管理员账号登录' : '登录你的账号，或注册普通用户账号' }}</p>
      <el-alert v-if="$route.query.registered === '1'" title="注册成功，请使用新账号登录" type="success" :closable="false" />
      <label>账号<el-input v-model="username" autocomplete="username" placeholder="请输入账号" size="large" /></label>
      <label>密码<el-input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" show-password size="large" /></label>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-button native-type="submit" type="primary" size="large" :loading="busy">登录</el-button>
      <RouterLink v-if="!adminOnly" to="/register">没有账号？立即注册</RouterLink>
      <p v-if="!adminOnly" class="muted">用户、数据管理员和服务器管理员使用各自账号进入对应页面。</p>
    </form>
  </section>
</template>
