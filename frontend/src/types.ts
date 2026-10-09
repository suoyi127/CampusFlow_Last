import type { ReviewSummary } from './reviewTypes'
export type Role = 'USER' | 'DATA_ADMIN' | 'SERVER_ADMIN'
export interface Account { id: number; username: string; role: Role; enabled: boolean }
export interface StudySpace {
  id: number; name: string; type: string; address: string; latitude: number; longitude: number
  capacity: number; openTime: string; closeTime: string; openDays: string; allDay: boolean
  facilities: string; description: string; enabled: boolean
}
export interface SpaceStatus {
  spaceId: number; simulationRunId: number; snapshotVersion: number | null; capacity: number
  currentPeople: number | null; occupancyRate: number | null; noiseDb: number | null
  typicalNoiseDb: number | null; quietLevel: number | null; peopleState: string; noiseState: string
  deviceState: string; peopleUpdatedAt: string | null; noiseUpdatedAt: string | null
  calculatedAt: string; validitySeconds: number; source: string
}
export interface SpaceCard {
  space: StudySpace; status: SpaceStatus; distanceMeters: number; score: number; openNow: boolean; reasons: string[]
  reviewSummary: ReviewSummary
}
export interface Page<T> { items: T[]; total: number; page: number; pageSize: number; calculatedAt: string; warnings: string[] }
export interface Overview {
  version: string; startedAt: string; accountCount: number; spaceCount: number; simulationRunId: number
  running: boolean; pollSeconds: number; simulationSeconds: number; validitySeconds: number; noiseWindowSeconds: number
  scenario: Scenario; seed: number; targetSpaceId: number | null; eventEndsAt: string | null
}
export type Scenario = 'NORMAL' | 'PEAK' | 'NOISE_EVENT' | 'DEVICE_OFFLINE'
export const scenarioNames: Record<Scenario, string> = { NORMAL: '正常人流', PEAK: '人流高峰', NOISE_EVENT: '噪声事件', DEVICE_OFFLINE: '设备离线' }
export interface NoiseDevice { id: number; spaceId: number; spaceName: string; enabled: boolean; deviceCode: string; status: 'ONLINE' | 'OFFLINE'; baseDb: number; fluctuationDb: number }
export type RecordKind = 'PEOPLE' | 'NOISE' | 'VISIT'
export interface SimulationRecord {
  id: number; runId: number; spaceId: number; sampledAt?: string; valid?: boolean; invalidReason?: string | null
  currentPeople?: number; noiseDb?: number; deviceId?: number; virtualPersonId?: string; checkedInAt?: string; checkedOutAt?: string | null
}
export const facilityNames: Record<string, string> = { AC: '空调', SEAT: '座椅', POWER: '插座', WIFI: '网络' }
export const typeNames: Record<string, string> = { LIBRARY: '图书馆', CLASSROOM: '教室', DISCUSSION: '研讨区', OUTDOOR: '室外', STUDY_ROOM: '自习室', CAFE: '咖啡厅' }
export const roleNames: Record<Role, string> = { USER: '用户', DATA_ADMIN: '数据管理员', SERVER_ADMIN: '服务器管理员' }
export function formatTime(time: string | null) {
  return time ? new Date(time).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '暂无记录'
}
export function metricIsRecent(status: SpaceStatus, time: string | null, state: string, elapsed: number) {
  return time !== null && state === 'VALID'
    && (Date.parse(status.calculatedAt) - Date.parse(time)) / 1000 + elapsed <= status.validitySeconds
}
export function recommendationIsStale(status: SpaceStatus, elapsed: number) {
  return (status.peopleState === 'VALID' && !metricIsRecent(status, status.peopleUpdatedAt, status.peopleState, elapsed))
    || (status.noiseState === 'VALID' && !metricIsRecent(status, status.noiseUpdatedAt, status.noiseState, elapsed))
}
