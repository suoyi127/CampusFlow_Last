<script setup lang="ts">
import { ref } from 'vue'
import { api, jsonRequest } from '../api'
import { router } from '../router'
const username=ref(''),password=ref(''),confirmation=ref(''),error=ref(''),busy=ref(false)
async function submit() {
  if(busy.value) return
  error.value=''
  if(!/^[A-Za-z0-9_]{3,64}$/.test(username.value)) {error.value='用户名须为 3—64 个字母、数字或下划线';return}
  if(password.value.length<8||new TextEncoder().encode(password.value).length>72) {error.value='密码至少 8 个字符且最多 72 个 UTF-8 字节';return}
  if(password.value!==confirmation.value) {error.value='两次密码不一致';return}
  busy.value=true
  try {
    await api('/auth/register',jsonRequest('POST',{username:username.value,password:password.value}))
    password.value='';confirmation.value=''
    await router.replace({path:'/login',query:{registered:'1'}})
  } catch(failure) {error.value=failure instanceof Error?failure.message:'注册失败，请重试'}
  finally {busy.value=false}
}
</script>

<template>
  <section class="login-layout">
    <div class="login-copy"><div class="eyebrow">加入 CampusFlow</div><h1>找到适合你的<br>学习空间。</h1><p>注册普通用户账号，比较空间并分享学习体验。</p></div>
    <form class="panel login-form" @submit.prevent="submit">
      <h2>创建账号</h2><p class="muted">注册后返回登录。管理员账号由服务器管理员分配。</p>
      <label>用户名<el-input v-model="username" autocomplete="username" placeholder="3—64 位字母、数字或下划线" :disabled="busy" size="large" /></label>
      <label>密码<el-input v-model="password" type="password" autocomplete="new-password" placeholder="至少 8 个字符，最多 72 UTF-8 字节" show-password :disabled="busy" size="large" /></label>
      <label>确认密码<el-input v-model="confirmation" type="password" autocomplete="new-password" placeholder="再次输入密码" show-password :disabled="busy" size="large" /></label>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-button native-type="submit" type="primary" size="large" :loading="busy">注册</el-button>
      <RouterLink to="/login">已有账号？返回登录</RouterLink>
    </form>
  </section>
</template>
