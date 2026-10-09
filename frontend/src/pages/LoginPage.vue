<script setup lang="ts">
import { ref } from 'vue'
import { login, account } from '../api'
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
    await router.replace(account.value?.role === 'DATA_ADMIN' ? '/data/spaces' : account.value?.role === 'SERVER_ADMIN' ? '/system' : '/spaces')
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '登录失败' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="login-layout">
    <div class="login-copy"><div class="eyebrow">找到你的学习节奏</div><h1>下一段专注，<br>从合适的空间开始。</h1><p>距离、安静程度、空闲座位与设施，<br>在一个页面里轻松比较。</p><div class="login-feature">01 / 选择条件　02 / 比较空间　03 / 开始学习</div></div>
    <form class="panel login-form" @submit.prevent="submit"><h2>欢迎回来</h2><p class="muted">使用分配给你的账号登录</p>
      <label>账号<el-input v-model="username" autocomplete="username" placeholder="请输入账号" size="large" /></label>
      <label>密码<el-input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" show-password size="large" /></label>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-button native-type="submit" type="primary" size="large" :loading="busy">登录</el-button>
      <p class="muted">用户、数据管理员和服务器管理员使用各自账号进入对应页面。</p>
    </form>
  </section>
</template>
