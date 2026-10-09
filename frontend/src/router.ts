import { createRouter, createWebHistory } from 'vue-router'
import { account, api, ApiFailure } from './api'
import type { Account } from './types'
import LoginPage from './pages/LoginPage.vue'
import RegisterPage from './pages/RegisterPage.vue'
import SpacesPage from './pages/SpacesPage.vue'
import DetailPage from './pages/DetailPage.vue'
import DataSpacesPage from './pages/DataSpacesPage.vue'
import SystemPage from './pages/SystemPage.vue'
import MyReviewsPage from './pages/MyReviewsPage.vue'
import DataReviewsPage from './pages/DataReviewsPage.vue'
import SystemAccountsPage from './pages/SystemAccountsPage.vue'
import SystemConfigPage from './pages/SystemConfigPage.vue'
import SystemLogsPage from './pages/SystemLogsPage.vue'
import DataInspectionPage from './pages/DataInspectionPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginPage },
    { path: '/register', component: RegisterPage },
    { path: '/', redirect: '/spaces' },
    { path: '/spaces', component: SpacesPage },
    { path: '/spaces/:id', component: DetailPage },
    { path: '/data/spaces', component: DataSpacesPage, meta: { role: 'DATA_ADMIN' } },
    { path: '/my/reviews', component: MyReviewsPage, meta: { role: 'USER' } },
    { path: '/data/reviews', component: DataReviewsPage, meta: { role: 'DATA_ADMIN' } },
    { path: '/data/inspection', component: DataInspectionPage, meta: { role: 'DATA_ADMIN' } },
    { path: '/system', component: SystemPage, meta: { role: 'SERVER_ADMIN' } },
    { path: '/system/accounts', component: SystemAccountsPage, meta: { role: 'SERVER_ADMIN' } },
    { path: '/system/config', component: SystemConfigPage, meta: { role: 'SERVER_ADMIN' } },
    { path: '/system/logs', component: SystemLogsPage, meta: { role: 'SERVER_ADMIN' } },
    { path: '/:pathMatch(.*)*', redirect: '/spaces' },
  ],
  scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
})
router.beforeEach(async to => {
  if (to.path === '/login' || to.path === '/register') return true
  if (!account.value) {
    try { account.value = await api<Account>('/auth/me') }
    catch (error) {
      if (!(error instanceof ApiFailure && error.status === 401)) console.error('无法恢复登录', error)
      return '/login'
    }
  }
  if (to.meta.role && account.value.role !== to.meta.role) return '/spaces'
})
window.addEventListener('campusflow-session-expired', () => { if (router.currentRoute.value.path !== '/login') void router.replace('/login') })
