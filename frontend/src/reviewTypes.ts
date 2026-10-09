import type { Page } from './types'

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'
export interface ReviewSummary { count: number; environmentAverage: number | null; facilityAverage: number | null }
export interface ReviewView {
  id: number; userId: number; username: string; spaceId: number; spaceName: string; spaceEnabled: boolean
  environmentScore: number; facilityScore: number; content: string; status: ReviewStatus
  reviewedBy: number | null; reviewerName: string | null; reviewedAt: string | null; reviewReason: string | null
  updatedAt: string; version: number
}
export interface PublicReview { id: number; author: string; environmentScore: number; facilityScore: number; content: string; updatedAt: string; reviewedAt: string }
export interface PublicReviewPage extends Page<PublicReview> { summary: ReviewSummary }
export interface MySpaceReview { review: ReviewView | null; spaceEnabled: boolean }
export const reviewStatusNames: Record<ReviewStatus,string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }
export const reviewStatusColors: Record<ReviewStatus,'warning'|'success'|'danger'|'info'> = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }
// 首次保存后的旧空响应和同一评价的低版本响应，都不能覆盖已确认的数据。
export function reviewIsOlder(current: ReviewView | null, incoming: ReviewView | null): boolean {
  return current !== null && (incoming === null || (current.id === incoming.id && incoming.version < current.version))
}
