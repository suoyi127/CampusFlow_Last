// @vitest-environment happy-dom
import { afterEach, expect, test, vi } from 'vitest'
import { browserLocation } from './browser-location'
import type { AMapSdk } from './types'
afterEach(()=>vi.unstubAllGlobals())
function setup(accuracy=20,errorCode?:number) {
  vi.stubGlobal('isSecureContext',true)
  const request=vi.fn((success:PositionCallback,error:PositionErrorCallback)=>errorCode?error({code:errorCode} as GeolocationPositionError):success({coords:{latitude:31,longitude:121,accuracy}} as GeolocationPosition))
  vi.stubGlobal('navigator',{geolocation:{getCurrentPosition:request}})
  const convert=vi.fn((_point:number[],_type:string,done:Function)=>done('complete',{locations:[{getLng:()=>121.005,getLat:()=>31.003}]}))
  return {request,convert,sdk:{convertFrom:convert} as unknown as AMapSdk}
}
test('requests browser location then converts GPS exactly once',async()=>{
  const {sdk,request,convert}=setup()
  expect(await browserLocation(sdk)).toEqual({longitude:121.005,latitude:31.003,accuracy:20,coordinateSystem:'GCJ02'})
  expect(request).toHaveBeenCalledWith(expect.any(Function),expect.any(Function),{enableHighAccuracy:true,timeout:8000,maximumAge:0})
  expect(convert).toHaveBeenCalledWith([121,31],'gps',expect.any(Function))
  expect(convert).toHaveBeenCalledOnce()
})
test('insecure context never starts location or conversion',async()=>{
  const {sdk,request,convert}=setup();vi.stubGlobal('isSecureContext',false)
  await expect(browserLocation(sdk)).rejects.toThrow('HTTPS')
  expect(request).not.toHaveBeenCalled();expect(convert).not.toHaveBeenCalled()
})
test.each([undefined,NaN,0,101,1000])('rejects missing or inaccurate precision %s',async accuracy=>{
  const {sdk,convert}=setup(accuracy===undefined?NaN:accuracy)
  await expect(browserLocation(sdk)).rejects.toThrow('精度')
  expect(convert).not.toHaveBeenCalled()
})
test.each([[1,'权限'],[2,'不可用'],[3,'超时']])('browser error %s does not fall back to IP',async(code,message)=>{
  const {sdk,convert}=setup(20,Number(code))
  await expect(browserLocation(sdk)).rejects.toThrow(String(message));expect(convert).not.toHaveBeenCalled()
})
test('does not convert an obsolete browser response',async()=>{
  const {sdk,convert}=setup()
  await expect(browserLocation(sdk,()=>false)).rejects.toThrow('取消')
  expect(convert).not.toHaveBeenCalled()
})
test('invalid conversion cannot masquerade as GCJ02',async()=>{
  const {sdk,convert}=setup();convert.mockImplementation((_p,_t,done:Function)=>done('error',{}))
  await expect(browserLocation(sdk)).rejects.toThrow('转换失败')
})
