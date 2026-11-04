# 定位链路检查（2026年10月9日）

更新：按用户要求已替换为显式浏览器定位，报告精度须在 100 米以内，再单次转换 GPS→GCJ02；应用不再使用高德 Geolocation 的 IP/城市回退。以下风险段落记录修复前的检查结论。实际浏览器授权及手机 HTTPS 精度仍待实机验收。

## 已验证

AMapIntegrationTest 增加坐标往返与距离检查：30.123456789 / 120.987654321 经空间 PUT、数据库 DOUBLE、详情 JSON 原值保留；以同坐标请求列表和详情，距离为 0；纬度增加 0.001 度，距离约 111.1949266 米。地图相关 5 项后端测试通过，测试事务回滚，不修改运行数据库。

后端 SpaceInput/StudySpace 为 Double，SpaceService 直接分别赋值 latitude/longitude，不转换或截断；地图标记按 [longitude, latitude] 构造，距离函数按 latitude/longitude 接收。定位插件设置 convert=true，没有后端二次坐标转换。显示的 toFixed(6) 仅是文本格式，手工数字控件限制六位精度；不代表数据库只支持六位。

## 尚未排除的风险

后端允许代理 /v3/ip，并原样转发参数。如果请求没有 ip 参数，高德将按请求来源 IP 定位，此时来源是后端服务器出口；不能据此获得手机精确位置。前端定位结果当前没有保存或展示 location_type，也未拒绝 IP / 城市定位回退。因此尚不能确认实际偏移是否由这一分支触发。

定位插件融合浏览器、IP 和 SDK 定位，enableHighAccuracy 仅要求尝试高精度，不保证成功。局域网 HTTP 页面、定位权限或设备环境可能导致精确定位不可用。演示空间坐标为合成数据，手工输入 WGS84 后误标 GCJ02 也无法仅靠经纬度数值识别。

## 建议排查与修复方向

在浏览器定位回调记录并展示 location_type、accuracy、isConverted（不要打印精确坐标或密钥），核对是否出现 /v3/ip 无 ip 请求。精确定位入口拒绝 IP / 城市回退并提示地图选点；保留 convert=true，不对结果盲目重复转换。不将任意 X-Forwarded-For 当作可信客户端公网 IP。

当前未捕获真实浏览器/手机定位回调，不能把代码检查或接口测试当作实机精度验收。

官方参考：[JS API 定位](https://lbs.amap.com/api/javascript-api-v2/guide/services/geolocation)、[定位参数](https://developer.amap.com/api/maps-javascript-api/reference/location/geolocation)、[IP 定位参数](https://lbs.amap.com/api/webservice/guide/api/ipconfig)。
