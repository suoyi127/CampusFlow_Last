import type { Role } from './types'
export interface ManagedAccount { id: number; username: string; role: Role; enabled: boolean; createdAt: string; version: number }
export interface RuntimeConfig {
  version: number; simulationSeconds: number; validitySeconds: number; noiseWindowSeconds: number
  distanceWeight: number; quietWeight: number; freeWeight: number; facilityWeight: number
}
export interface SystemLog { id: number; actor: string; action: string; target: string; result: string; reason: string | null; occurredAt: string }
