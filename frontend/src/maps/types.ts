export interface MapLocation {
  longitude: number
  latitude: number
  coordinateSystem: 'GCJ02'
  address?: string
  accuracy?: number
}

export interface LngLat { getLng(): number; getLat(): number }
export interface MapEvent { lnglat: LngLat }
export interface AMapMap {
  on(event: string, handler: (event: MapEvent) => void): void
  setCenter(position: number[]): void
  setFitView(overlays?: unknown[]): void
  remove(overlays: unknown | unknown[]): void
  destroy(): void
}
export interface AMapMarker {
  setPosition(position: number[]): void
  on(event: string, handler: (event: MapEvent) => void): void
}
export interface Poi { id?: string; name: string; address?: string; location: LngLat }
export interface AMapSdk {
  convertFrom(position: number[], type: 'gps', callback: (status: string, result: { locations?: LngLat[] }) => void): void
  Map: new (element: HTMLElement, options: Record<string, unknown>) => AMapMap
  Marker: new (options: Record<string, unknown>) => AMapMarker
  Circle: new (options: Record<string, unknown>) => unknown
  PlaceSearch: new (options: Record<string, unknown>) => { search(keyword: string, callback: (status: string, result: { poiList?: { pois: Poi[] } }) => void): void }
  Geocoder: new (options: Record<string, unknown>) => { getAddress(position: number[], callback: (status: string, result: { regeocode?: { formattedAddress: string } }) => void): void }
  Geolocation: new (options: Record<string, unknown>) => { getCurrentPosition(callback: (status: string, result: { position?: LngLat; accuracy?: number; formattedAddress?: string; message?: string }) => void): void }
  plugin(names: string[], callback: () => void): void
}

declare global {
  interface Window { AMap?: AMapSdk; _AMapSecurityConfig?: { serviceHost: string } }
}
