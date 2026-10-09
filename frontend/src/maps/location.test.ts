import { expect, test } from 'vitest'
import { validLocation, LatestSelection } from './location'

test('coordinates reject non-finite values and incompatible coordinate systems', () => {
  expect(validLocation({ longitude: 121.4737, latitude: 31.2304, coordinateSystem: 'GCJ02' })).toBe(true)
  expect(validLocation({ longitude: NaN, latitude: 31, coordinateSystem: 'GCJ02' })).toBe(false)
  expect(validLocation({ longitude: 181, latitude: 31, coordinateSystem: 'GCJ02' })).toBe(false)
  expect(validLocation({ longitude: 121, latitude: 91, coordinateSystem: 'GCJ02' })).toBe(false)
  expect(validLocation({ longitude: 121, latitude: 31, coordinateSystem: 'WGS84' })).toBe(false)
})

test('a delayed geocoder or location callback cannot replace a later selection or run after close', () => {
  const selection = new LatestSelection()
  const old = selection.begin(), latest = selection.begin()
  expect(selection.isCurrent(old)).toBe(false)
  expect(selection.isCurrent(latest)).toBe(true)
  selection.close()
  expect(selection.isCurrent(latest)).toBe(false)
})
