import { reactive } from 'vue'

// 保留页面离开前的条件，详情返回和网络重试都使用同一组筛选。
export const filters = reactive({
  name: '', type: '', maxDistance: 3000, minQuiet: null as number | null,
  maxOccupancy: null as number | null, facilities: [] as string[], openOnly: true,
  sort: 'SCORE', latitude: 31.2304, longitude: 121.4737, startAt: '', durationMinutes: 0,
})
export function queryString() {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value === null || value === undefined || value === '' || Array.isArray(value)) continue
    query.set(key, String(value))
  }
  filters.facilities.forEach(value => query.append('facilities', value))
  if (filters.startAt) query.set('startAt', new Date(filters.startAt).toISOString())
  query.set('pageSize', '100')
  return query.toString()
}

export function detailOrigin(query: Record<string, unknown>) {
  const latitude = typeof query.latitude === 'string' && query.latitude.trim() ? Number(query.latitude) : NaN
  const longitude = typeof query.longitude === 'string' && query.longitude.trim() ? Number(query.longitude) : NaN
  // 详情链接必须携带完整、合法的一对坐标，避免复制链接后距离回到默认起点。
  return Number.isFinite(latitude) && Number.isFinite(longitude) && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
    ? { latitude, longitude } : { latitude: filters.latitude, longitude: filters.longitude }
}
