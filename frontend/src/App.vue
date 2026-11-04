<script setup lang="ts">
import { ref, watch } from 'vue'
import { account, logout } from './api'
import { router } from './router'
import { roleNames } from './types'
import { ElMessage } from 'element-plus'
const navigationOpen = ref(false)
watch(() => router.currentRoute.value.path, () => { navigationOpen.value = false })
async function signOut() {
  try { await logout(); await router.replace('/login') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '退出失败') }
}
</script>

<template>
  <div class="app-shell" :class="{ 'has-sidebar': account, 'navigation-open': navigationOpen }" @keydown.esc="navigationOpen = false">
  <header class="topbar">
    <button v-if="account" class="navigation-toggle" type="button" :aria-expanded="navigationOpen" aria-controls="primary-navigation" @click="navigationOpen = !navigationOpen">{{ navigationOpen ? '关闭菜单' : '☰ 菜单' }}</button>
    <RouterLink to="/spaces" class="brand"><span class="brand-mark">C</span><span>CampusFlow<small>校园学习空间</small></span></RouterLink>

    <div v-if="account" class="account"><span>{{ account.username }} · {{ roleNames[account.role] }}</span><el-button text @click="signOut">退出</el-button></div>
  </header>
  <button v-if="account && navigationOpen" class="sidebar-backdrop" aria-label="关闭导航" @click="navigationOpen = false"></button>
  <aside v-if="account" class="sidebar"><div class="sidebar-label">功能导航</div>
    <nav id="primary-navigation" aria-label="功能导航">
      <RouterLink to="/spaces">找空间</RouterLink>
      <RouterLink v-if="account.role === 'USER'" to="/my/reviews">我的评价</RouterLink>
      <RouterLink v-if="account.role === 'DATA_ADMIN'" to="/data/spaces">空间管理</RouterLink>
      <RouterLink v-if="account.role === 'DATA_ADMIN'" to="/data/hardware-devices">设备管理</RouterLink>
      <RouterLink v-if="account.role === 'DATA_ADMIN'" to="/data/reviews">评价审核</RouterLink>
      <RouterLink v-if="account.role === 'DATA_ADMIN'" to="/data/inspection">数据检查</RouterLink>
      <RouterLink v-if="account.role === 'SERVER_ADMIN'" to="/system">服务器概况</RouterLink>
      <RouterLink v-if="account.role === 'SERVER_ADMIN'" to="/system/accounts">账号管理</RouterLink>
      <RouterLink v-if="account.role === 'SERVER_ADMIN'" to="/system/config">运行配置</RouterLink>
      <RouterLink v-if="account.role === 'SERVER_ADMIN'" to="/system/logs">操作日志</RouterLink>
    </nav>
  </aside>
  <main><RouterView :key="$route.path" /></main>
  <footer>CampusFlow · 课堂演示 · 实时指标来自服务器模拟</footer>
  </div>
</template>
