import type { AMapSdk } from './types'
import { api } from '../api'

let pending: Promise<AMapSdk> | null = null

export function loadAMap(): Promise<AMapSdk> {
  if (pending) return pending
  const attempt = api<{ configured: boolean; key: string }>('/amap/config').then(config => {
    if (!config.configured || !config.key) throw new Error('地图未配置高德应用 Key，可继续使用列表、预置点或手动坐标。')
    return new Promise<AMapSdk>((resolve, reject) => {
    // 安全密钥只由后端代理注入，浏览器只知道同源代理地址。
    window._AMapSecurityConfig = { serviceHost: `${window.location.origin}/_AMapService` }
    const script = document.createElement('script')
    script.dataset.campusflowAmap = 'true'; script.async = true
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(config.key)}`
    let settled = false
    const timer = setTimeout(() => fail(), 12000)
    function fail() {
      if (settled) return
      settled = true; clearTimeout(timer); script.remove()
      reject(new Error('地图加载失败，请检查网络与高德配置后重试；列表和手动选点仍可使用。'))
    }
    script.onerror = fail
    script.onload = () => {
      if (settled) return
      const sdk = window.AMap
      if (!sdk) { fail(); return }
      sdk.plugin(['AMap.PlaceSearch', 'AMap.Geocoder'], () => {
        if (settled) return
        settled = true; clearTimeout(timer); resolve(sdk)
      })
    }
    try { document.head.appendChild(script) } catch { fail() }
    })
  })
  pending = attempt
  void attempt.catch(() => { if (pending === attempt) pending = null })
  return attempt
}
