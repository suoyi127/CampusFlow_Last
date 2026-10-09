import { describe, expect, it } from 'vitest'
import { filters, queryString, detailOrigin } from './filters'

describe('地图起点与查询', () => {
  it('详情优先保留链接携带的有效起点，缺失或非法则回退当前起点', () => {
    expect(detailOrigin({ latitude: '30.1', longitude: '120.2' })).toEqual({ latitude: 30.1, longitude: 120.2 })
    expect(detailOrigin({ latitude: '91', longitude: '120' })).toEqual({ latitude: filters.latitude, longitude: filters.longitude })
    expect(detailOrigin({ latitude: '', longitude: '' })).toEqual({ latitude: filters.latitude, longitude: filters.longitude })
  })
  it('地图与列表使用同一批至多100个查询结果', () => {
    expect(new URLSearchParams(queryString()).get('pageSize')).toBe('100')
  })
})
