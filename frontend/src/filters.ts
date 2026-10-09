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
  return query.toString()
}
