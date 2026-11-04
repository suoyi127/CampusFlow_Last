import type { AMapSdk, MapLocation } from './types'
import { callbackRequest, validLocation } from './location'

export async function browserLocation(sdk: AMapSdk, isCurrent = () => true): Promise<MapLocation> {
  if (!window.isSecureContext) throw new Error('浏览器定位需要 HTTPS；电脑可用 localhost，手机请使用 HTTPS 或地图选点。')
  if (!navigator.geolocation) throw new Error('浏览器不支持定位，请地图选点。')
  const position = await callbackRequest<GeolocationPosition>((done, fail) => {
    navigator.geolocation.getCurrentPosition(done, error => {
      fail(error.code === 1 ? '位置权限被拒绝，请在浏览器设置中允许位置权限，或地图选点。'
        : error.code === 3 ? '浏览器定位超时，请重试或地图选点。' : '浏览器位置不可用，请检查系统定位服务，或地图选点。')
    }, { enableHighAccuracy: true, timeout: 8000, maximumAge: 0 })
  })
  if (!isCurrent()) throw new Error('定位已取消')
  const { longitude, latitude, accuracy } = position.coords
  if (!Number.isFinite(accuracy) || accuracy <= 0 || accuracy > 100)
    throw new Error('定位精度未知或超过 100 米，未应用位置，请重试或地图选点。')
  if (!validLocation({ longitude, latitude, coordinateSystem: 'GCJ02' }))
    throw new Error('浏览器返回了无效坐标，请重试或地图选点。')
  // 浏览器坐标按 GPS/WGS84 处理；只转换一次，失败不能直接贴上 GCJ02 标签。
  return callbackRequest<MapLocation>((done, fail) => sdk.convertFrom([longitude, latitude], 'gps', (status, result) => {
    if (!isCurrent()) { fail('定位已取消'); return }
    const point = result.locations?.[0]
    if (status !== 'complete' || !point) { fail('高德坐标转换失败，未应用位置，请重试或地图选点。'); return }
    const converted: MapLocation = { longitude: point.getLng(), latitude: point.getLat(), accuracy, coordinateSystem: 'GCJ02' }
    if (!validLocation(converted)) { fail('高德坐标转换失败，返回坐标无效'); return }
    done(converted)
  }))
}
