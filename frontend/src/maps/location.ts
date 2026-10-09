import type { MapLocation } from './types'

export function validLocation(point: { longitude: number; latitude: number; coordinateSystem: string }): point is MapLocation {
  return point.coordinateSystem === 'GCJ02' && Number.isFinite(point.longitude) && Number.isFinite(point.latitude)
    && Math.abs(point.longitude) <= 180 && Math.abs(point.latitude) <= 90
}

// 点击、搜索与定位共享版本；晚到的回调不能替换新选点，关闭后也不能写入。
export class LatestSelection {
  private revision = 0
  private active = true
  begin() { return ++this.revision }
  isCurrent(revision: number) { return this.active && revision === this.revision }
  close() { this.active = false; this.revision++ }
}

export function callbackRequest<T>(start: (done: (result: T) => void, fail: (message: string) => void) => void): Promise<T> {
  return new Promise((resolve, reject) => {
    let settled = false
    const timer = setTimeout(() => finish(undefined, '地图服务请求超时，请重试或手动选点'), 12000)
    function finish(result?: T, error?: string) {
      if (settled) return
      settled = true; clearTimeout(timer)
      if (error) reject(new Error(error)); else resolve(result as T)
    }
    try { start(result => finish(result), message => finish(undefined, message)) }
    catch { finish(undefined, '地图服务暂不可用，请重试') }
  })
}
