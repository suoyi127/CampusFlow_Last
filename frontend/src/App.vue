<script setup lang="ts">
import { account, logout } from './api'
import { router } from './router'
import { roleNames } from './types'
import { ElMessage } from 'element-plus'
async function signOut() {
  try { await logout(); await router.replace('/login') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '退出失败') }
}
</script>

<template>
  <header class="topbar">
    <RouterLink to="/spaces" class="brand"><span class="brand-mark">C</span><span>CampusFlow<small>校园学习空间</small></span></RouterLink>
    <nav v-if="account">
      <RouterLink to="/spaces">找空间</RouterLink>
      <RouterLink v-if="account.role === 'USER'" to="/my/reviews">我的评价</RouterLink>
      <RouterLink v-if="account.role === 'DATA_ADMIN'" to="/data/spaces">空间管理</RouterLink>
      <RouterLink v-if="account.role === 'DATA_ADMIN'" to="/data/reviews">评价审核</RouterLink>
      <RouterLink v-if="account.role === 'DATA_ADMIN'" to="/data/inspection">数据检查</RouterLink>
      <RouterLink v-if="account.role === 'SERVER_ADMIN'" to="/system">服务器概况</RouterLink>
      <RouterLink v-if="account.role === 'SERVER_ADMIN'" to="/system/accounts">账号管理</RouterLink>
      <RouterLink v-if="account.role === 'SERVER_ADMIN'" to="/system/config">运行配置</RouterLink>
      <RouterLink v-if="account.role === 'SERVER_ADMIN'" to="/system/logs">操作日志</RouterLink>
    </nav>
    <div v-if="account" class="account"><span>{{ account.username }} · {{ roleNames[account.role] }}</span><el-button text @click="signOut">退出</el-button></div>
  </header>
  <main><RouterView :key="$route.path" /></main>
  <footer>CampusFlow · 课堂演示 · 实时指标来自服务器模拟</footer>
</template>
